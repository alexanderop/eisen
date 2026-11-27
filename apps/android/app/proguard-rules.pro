# ML Kit finds its parts by class name and builds each with its no-argument constructor. R8 removes a
# constructor nothing calls, and then the code scanner dies with a NullPointerException in getClient().
# The same holds for the text recognition that reads a photo of a label: without its TextRegistrar the release
# build logs "ComponentDiscovery: Could not instantiate ...TextRegistrar" and no photo is ever read
# (eisenctl release-smoke fails on it).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
