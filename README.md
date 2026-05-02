# OnymSDK Kotlin — Maven repository

This branch is a **static Maven repository** for OnymSDK Kotlin
releases. Do not commit here directly — `scripts/publish-to-releases-branch.sh`
in the `main` branch overwrites everything here on every release.

## Consume from a Gradle project

```kotlin
repositories {
    maven { url = uri("https://raw.githubusercontent.com/onymchat/onym-sdk-kotlin/releases/") }
}
dependencies {
    implementation("chat.onym:onym-sdk:0.0.1")
}
```

Released versions are listed in `chat/onym/onym-sdk/maven-metadata.xml`.
