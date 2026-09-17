# dev7–dev8 实际 APK 运行时依赖

从 Gradle debugRuntimeClasspath 解析并核对缓存中的对应版本 POM（含父 POM）。共 89 个构件，声明均为 Apache 2.0；原始打包声明见 THIRD_PARTY_NOTICES.txt。

| 构件 | POM 许可证声明 |
|---|---|
| `androidx.activity:activity:1.10.1` | The Apache Software License, Version 2.0 |
| `androidx.activity:activity-compose:1.10.1` | The Apache Software License, Version 2.0 |
| `androidx.activity:activity-ktx:1.10.1` | The Apache Software License, Version 2.0 |
| `androidx.annotation:annotation-experimental:1.4.1` | The Apache Software License, Version 2.0 |
| `androidx.annotation:annotation-jvm:1.9.1` | The Apache Software License, Version 2.0 |
| `androidx.appcompat:appcompat-resources:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.arch.core:core-common:2.2.0` | The Apache Software License, Version 2.0 |
| `androidx.arch.core:core-runtime:2.2.0` | The Apache Software License, Version 2.0 |
| `androidx.autofill:autofill:1.0.0` | The Apache Software License, Version 2.0 |
| `androidx.collection:collection-jvm:1.5.0` | The Apache Software License, Version 2.0 |
| `androidx.collection:collection-ktx:1.5.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.animation:animation-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.animation:animation-core-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.foundation:foundation-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.foundation:foundation-layout-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-icons-core-android:1.7.8` | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-ripple-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-saveable-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-geometry-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-graphics-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-test-manifest:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-text-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-tooling-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-tooling-data-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-tooling-preview-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-unit-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-util-android:1.8.0` | The Apache Software License, Version 2.0 |
| `androidx.concurrent:concurrent-futures:1.1.0` | The Apache Software License, Version 2.0 |
| `androidx.core:core:1.16.0` | The Apache Software License, Version 2.0 |
| `androidx.core:core-ktx:1.16.0` | The Apache Software License, Version 2.0 |
| `androidx.core:core-viewtree:1.0.0` | The Apache Software License, Version 2.0 |
| `androidx.customview:customview:1.0.0` | The Apache Software License, Version 2.0 |
| `androidx.customview:customview-poolingcontainer:1.0.0` | The Apache Software License, Version 2.0 |
| `androidx.emoji2:emoji2:1.4.0` | The Apache Software License, Version 2.0 |
| `androidx.exifinterface:exifinterface:1.3.7` | The Apache Software License, Version 2.0 |
| `androidx.graphics:graphics-path:1.0.1` | The Apache Software License, Version 2.0 |
| `androidx.interpolator:interpolator:1.0.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-process:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.0` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-common:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-container:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-database:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-datasource:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-datasource-okhttp:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-decoder:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-exoplayer:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-exoplayer-dash:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-exoplayer-hls:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-extractor:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.media3:media3-ui:1.6.1` | The Apache Software License, Version 2.0 |
| `androidx.profileinstaller:profileinstaller:1.4.0` | The Apache Software License, Version 2.0 |
| `androidx.recyclerview:recyclerview:1.3.0` | The Apache Software License, Version 2.0 |
| `androidx.savedstate:savedstate-android:1.3.0` | The Apache Software License, Version 2.0 |
| `androidx.savedstate:savedstate-ktx:1.3.0` | The Apache Software License, Version 2.0 |
| `androidx.startup:startup-runtime:1.1.1` | The Apache Software License, Version 2.0 |
| `androidx.tracing:tracing:1.2.0` | The Apache Software License, Version 2.0 |
| `androidx.tv:tv-material:1.0.1` | The Apache Software License, Version 2.0 |
| `androidx.vectordrawable:vectordrawable:1.1.0` | The Apache Software License, Version 2.0 |
| `androidx.vectordrawable:vectordrawable-animated:1.1.0` | The Apache Software License, Version 2.0 |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | The Apache Software License, Version 2.0 |
| `com.google.accompanist:accompanist-drawablepainter:0.32.0` | The Apache Software License, Version 2.0 |
| `com.google.guava:failureaccess:1.0.2` | The Apache Software License, Version 2.0 |
| `com.google.guava:guava:33.3.1-android` | Apache License, Version 2.0 |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | The Apache Software License, Version 2.0 |
| `com.squareup.okhttp3:okhttp:4.12.0` | The Apache Software License, Version 2.0 |
| `com.squareup.okio:okio-jvm:3.9.0` | The Apache Software License, Version 2.0 |
| `io.coil-kt:coil:2.7.0` | The Apache License, Version 2.0 |
| `io.coil-kt:coil-base:2.7.0` | The Apache License, Version 2.0 |
| `io.coil-kt:coil-compose:2.7.0` | The Apache License, Version 2.0 |
| `io.coil-kt:coil-compose-base:2.7.0` | The Apache License, Version 2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.1.20` | The Apache License, Version 2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0` | The Apache License, Version 2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0` | The Apache License, Version 2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | The Apache Software License, Version 2.0 |
| `org.jetbrains:annotations:23.0.0` | The Apache Software License, Version 2.0 |
| `org.jspecify:jspecify:1.0.0` | The Apache License, Version 2.0 |
