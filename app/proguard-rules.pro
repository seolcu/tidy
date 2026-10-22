# WorkManager (initialized at startup via androidx.startup) builds its Room
# database by reflectively calling WorkDatabase_Impl's no-arg constructor.
# Under AGP 9's R8 that constructor gets stripped and the release build
# crashes before any app code runs (seen in WakeLink 0.2.0-0.2.2). Keep
# generated Room database constructors.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
