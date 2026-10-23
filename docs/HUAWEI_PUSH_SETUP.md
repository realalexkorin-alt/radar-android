# Remaining manual dependency: Huawei Push Kit

Actual provider push is not implemented/enabled in 0.1.28. Local notifications work on sync while app or foreground monitoring runs. No available agconnect-services.json / provider app configuration was found in the repository or matching saved files.

1. In Huawei AppGallery Connect create/select an Android app for package `dev.raf.radar` and enable Push Kit. Account registration/verification or agreement acceptance must be completed by the account owner.
2. Register release signing SHA-256: `7BAF77AFC2F4B5C570C9B08D6092F55AD3EC10A2D798649C846BF2A1429BBD89`.
3. Download app configuration `agconnect-services.json` with **Do not include key** selected. Server-side client secrets belong only in Vercel protected environment settings, never APK, public git, release notes, or chat.
4. Once this provider app exists, implement and validate token registration, request-event delivery, deep link, token rotation and delivery on the physical Huawei with app closed. This is a separate small release, not claimed complete by polling fallback.

Official integration instructions: https://developer.huawei.com/consumer/en/codelab/HMSPushKit/index.html
