# Masarifi release (R8) rules.
#
# Backup / sync / CSV / repair-snapshot JSON is written with org.json using string literals for every field name
# (BackupCodec, SyncCodec, RepairSnapshot, SyncStore), so obfuscation cannot change the file format. The rules below
# are belt-and-braces for anything that could be reached by name.

# Data layer: Room entities, DAOs, the database and backup/sync models keep their names and members
# (Room ships its own consumer rules for generated *_Impl classes; this keeps stack traces and any reflective use readable).
-keep class com.midroid.masarifi.data.** { *; }
-keep class com.midroid.masarifi.analysis.** { *; }

# SMS: receivers are referenced from the manifest (kept by AAPT rules); parser config / models kept for safety.
-keep class com.midroid.masarifi.sms.** { *; }

# WorkManager workers are instantiated by class name.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Money Manager .xlsx reader uses the platform SAX parser with DefaultHandler subclasses.
-keep class * extends org.xml.sax.helpers.DefaultHandler { *; }

# Enums: valueOf/values via reflection.
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Keep source file / line numbers for crash reports, but hide the original source file name.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile
