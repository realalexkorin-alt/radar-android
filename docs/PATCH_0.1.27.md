# 0.1.27-alpha — accounts

- Explicit Register / Login forms. Immutable chosen free ID 1–999 or random free three-digit ID, password and changeable nickname. Server serializes ID allocation.
- Salted scrypt passwords, random opaque sessions hashed on server; session encrypted with Android Keystore on device. Passwords are not saved on device.
- One-time 16-digit recovery code displayed for copying/screenshot. Recovery rotates the code and revokes old sessions; password changes require the old password and confirmation, revoke other sessions.
- Auth rate limiting by source and ID; server-side ownership/friendship checks. No database secrets in APK.
- Existing local saved points/preferences preserved. Prove old installation capability to migrate last shared point into private account; remove migrated public coordinate.
- Account positions are private, retained indefinitely, and readable only by accepted friends. Public legacy clients remain on their old endpoint/retention policy.
- Friend management UI follows in a separate patch; new accounts start with an empty friend list.
- Focused validation: auth contract tests, native login/recovery form and encrypted session tests; live API account lifecycle verification before release.
