# Validation scope for 0.1.68

The default Android checks profile now runs current monitoring regression tests on API 30 and 35: FareScriptLifecycleTest, ConnectionRecoveryTest, GpsQualityTest, UpdateArchiveTest, StartupSoundTest; permission entry, baseline data migration and real system-installer upgrade remain required. All JVM and backend tests run.

The optional presence/full legacy profiles remain available. The first run exposed unrelated pre-existing mismatches: PresencePatchTest calls a retired anonymous production endpoint (HTTP 400), several UI assertions expect the previous navigation/editor and EconomyPatchTest expects one widget child and automatic plus prefix. Those failures have not been claimed as fixed; they need migration to the current account/UI design separately. No production endpoint is called by the new default monitoring fixture tests.

Live owner-device GPS, screen-off scheduling and Yandex session behavior still require device validation.
