# ClearUp keeps its data model deliberately simple. Add only targeted rules when
# a concrete library requires them; avoid broad keep rules that weaken R8.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku instantiates this UserService by class name in a remote process.
-keep class com.norvexa.clearup.data.privilege.ShizukuCommandService { public <init>(...); *; }
-keep interface com.norvexa.clearup.data.privilege.IShizukuCommandService { *; }
