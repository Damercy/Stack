-keep class com.stackapp.stack.storage.StackDatabase_Impl { *; }

# The instrumentation runner loads this shared runtime class by its original name.
-keep,allowoptimization class androidx.tracing.Trace { public static *; }

# Firebase, Play Billing, Room, WorkManager, and Compose ship consumer rules.
# Keep this file focused on app-specific rules so release shrink behavior stays
# easy to review before Play uploads.
