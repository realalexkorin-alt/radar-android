import {database} from './postgres.js';
let ready;
export async function initialize(){const sql=database();if(!ready)ready=sql.transaction([
 sql`SELECT pg_advisory_xact_lock(72915033)`,
 sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.stat_regions(id bigserial PRIMARY KEY,lat double precision NOT NULL,lon double precision NOT NULL)`),
 sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.observations(observation_id uuid PRIMARY KEY,user_id integer NOT NULL REFERENCES radar_presence.accounts(id) ON DELETE CASCADE,source_key text NOT NULL,measured_at bigint NOT NULL,bucket bigint NOT NULL,lat double precision NOT NULL,lon double precision NOT NULL,rub integer NOT NULL CHECK(rub>=0),region_id bigint NOT NULL REFERENCES radar_presence.stat_regions(id),calendar jsonb NOT NULL,weather jsonb,version text NOT NULL,received_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(user_id,source_key,bucket))`),
 sql.query(`CREATE INDEX IF NOT EXISTS radar_observations_region_time ON radar_presence.observations(region_id,measured_at DESC)`),
 sql.query(`CREATE INDEX IF NOT EXISTS radar_observations_expiry ON radar_presence.observations(received_at)`),
 sql.query(`CREATE INDEX IF NOT EXISTS radar_observations_source_time ON radar_presence.observations(user_id,source_key,measured_at DESC)`),
 sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.stat_sources(user_id integer NOT NULL REFERENCES radar_presence.accounts(id) ON DELETE CASCADE,source_key text NOT NULL,name text NOT NULL,lat double precision NOT NULL,lon double precision NOT NULL,revision bigint NOT NULL,updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(user_id,source_key))`),
 sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.weather_cache(cell text PRIMARY KEY,payload jsonb NOT NULL,expires_at timestamptz NOT NULL)`),
 sql.query(`CREATE OR REPLACE FUNCTION radar_presence.cleanup_statistics() RETURNS void LANGUAGE sql AS $$ DELETE FROM radar_presence.weather_cache WHERE expires_at<=clock_timestamp(); DELETE FROM radar_presence.stat_regions r WHERE NOT EXISTS(SELECT 1 FROM radar_presence.observations o WHERE o.region_id=r.id); $$`),
 sql.query(`SELECT cron.schedule('radar-statistics-expiry','17 * * * *','SELECT radar_presence.cleanup_statistics()') WHERE NOT EXISTS(SELECT 1 FROM cron.job WHERE jobname='radar-statistics-expiry')`)
 ]).catch(e=>{ready=null;throw e;});return ready;}
export const statisticsStore={
 async putSources(user,rows){if(!rows.length)return;await initialize();const sql=database();await sql.transaction(rows.map(row=>sql`INSERT INTO radar_presence.stat_sources AS existing_source(user_id,source_key,name,lat,lon,revision) VALUES(${user},${row.sourceKey},${row.name},${row.lat},${row.lon},${row.revision}) ON CONFLICT(user_id,source_key) DO UPDATE SET name=EXCLUDED.name,lat=EXCLUDED.lat,lon=EXCLUDED.lon,revision=EXCLUDED.revision,updated_at=clock_timestamp() WHERE existing_source.revision<EXCLUDED.revision`));},
 async pointRows(user,key,lat,lon,date,timezone){await initialize();const sql=database();return sql`WITH cells AS (
 SELECT bucket,avg(rub)::float AS rub,count(*)::int AS samples,max(measured_at) AS last_at,to_timestamp(min(measured_at)/1000.0) AT TIME ZONE ${timezone} AS local_time
 FROM radar_presence.observations WHERE user_id=${user} AND source_key=${key}
 AND measured_at>=extract(epoch FROM ((${date}::date-84)::timestamp AT TIME ZONE ${timezone}))*1000
 AND measured_at<extract(epoch FROM ((${date}::date+1)::timestamp AT TIME ZONE ${timezone}))*1000
 AND 6371000*2*asin(sqrt(least(1.0,power(sin(radians(lat-${lat})/2),2)+cos(radians(lat))*cos(radians(${lat}))*power(sin(radians(lon-${lon})/2),2))))<=100
 GROUP BY bucket
 ) SELECT to_char(local_time,'YYYY-MM-DD') AS date,extract(hour FROM local_time)::int AS hour,avg(rub)::float AS rub,sum(samples)::int AS samples,max(last_at)::float AS "lastAt" FROM cells GROUP BY to_char(local_time,'YYYY-MM-DD'),extract(hour FROM local_time) ORDER BY date,hour`;},
 async pointDayRows(user,key,lat,lon,date,timezone){await initialize();const sql=database();return sql`SELECT measured_at::float AS at,rub FROM radar_presence.observations
 WHERE user_id=${user} AND source_key=${key}
 AND measured_at>=extract(epoch FROM (${date}::date::timestamp AT TIME ZONE ${timezone}))*1000
 AND measured_at<extract(epoch FROM ((${date}::date+1)::timestamp AT TIME ZONE ${timezone}))*1000
 AND 6371000*2*asin(sqrt(least(1.0,power(sin(radians(lat-${lat})/2),2)+cos(radians(lat))*cos(radians(${lat}))*power(sin(radians(lon-${lon})/2),2))))<=100 ORDER BY measured_at LIMIT 2000`;},
 async weatherGet(key){await initialize();const sql=database();return (await sql`SELECT payload FROM radar_presence.weather_cache WHERE cell=${key} AND expires_at>clock_timestamp()`)[0]?.payload;},
 async weatherPut(key,payload){const sql=database();await sql`INSERT INTO radar_presence.weather_cache(cell,payload,expires_at) VALUES(${key},${JSON.stringify(payload)}::jsonb,clock_timestamp()+interval '30 minutes') ON CONFLICT(cell) DO UPDATE SET payload=EXCLUDED.payload,expires_at=EXCLUDED.expires_at`;},
 async put(user,rows){await initialize();const sql=database();await sql.transaction([sql`SELECT pg_advisory_xact_lock(72915033)`,...rows.map(row=>sql.query(`WITH nearest AS (SELECT id FROM radar_presence.stat_regions WHERE 6371000*2*asin(sqrt(least(1.0,power(sin(radians(lat-$1)/2),2)+cos(radians(lat))*cos(radians($1))*power(sin(radians(lon-$2)/2),2))))<=1000 ORDER BY power(lat-$1,2)+power(lon-$2,2) LIMIT 1), created AS(INSERT INTO radar_presence.stat_regions(lat,lon) SELECT $1,$2 WHERE NOT EXISTS(SELECT 1 FROM nearest) RETURNING id), region AS(SELECT id FROM nearest UNION ALL SELECT id FROM created) INSERT INTO radar_presence.observations(observation_id,user_id,source_key,measured_at,bucket,lat,lon,rub,region_id,calendar,weather,version) SELECT $3::uuid,$4,$5,$6,$7,$1,$2,$8,id,$9::jsonb,$10::jsonb,$11 FROM region ON CONFLICT DO NOTHING`,[row.lat,row.lon,row.observationId,user,row.sourceKey,row.measuredAt,Math.floor(row.measuredAt/300000),row.rub,JSON.stringify(row.calendar),row.weather?JSON.stringify(row.weather):null,row.version]))]);},
 async read(user,limit){await initialize();const sql=database();const summary=(await sql`SELECT count(*)::int AS samples,count(DISTINCT region_id)::int AS regions,count(*) FILTER(WHERE weather IS NOT NULL)::int AS "withWeather",min(measured_at) AS "firstAt",max(measured_at) AS "lastAt" FROM radar_presence.observations WHERE (${user}::integer IS NULL OR user_id=${user})`)[0];const rows=await sql`SELECT o.measured_at AS "measuredAt",o.rub,o.region_id AS "regionId",o.lat,o.lon,o.calendar,o.weather,o.version,o.source_key AS "sourceKey",s.name AS "sourceName" FROM radar_presence.observations o LEFT JOIN radar_presence.stat_sources s ON s.user_id=o.user_id AND s.source_key=o.source_key WHERE (${user}::integer IS NULL OR o.user_id=${user}) ORDER BY o.measured_at DESC LIMIT ${limit}`;return {summary,rows,retentionDays:null,radiusMeters:1000};}
};


