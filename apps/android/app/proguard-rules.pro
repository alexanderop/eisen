# ML Kit finds its parts by class name and builds each with its no-argument constructor. R8 removes a
# constructor nothing calls, and then the code scanner dies with a NullPointerException in getClient().
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
