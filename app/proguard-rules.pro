# Room and Media3 ship their own consumer rules; nothing app-specific is reflected on.

# Settings store enum values by name (theme, sort, pulse style, splash) and song sources travel in
# media item extras by name, so enum constant names must survive obfuscation.
-keepclassmembers enum com.centimusic.player.** { <fields>; }
