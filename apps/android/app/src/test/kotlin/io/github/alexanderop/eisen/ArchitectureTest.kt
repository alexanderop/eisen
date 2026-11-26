package io.github.alexanderop.eisen

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The package rules of `ARCHITECTURE.md`, checked on the compiled classes.
 *
 * Packages are named by kind: a segment `ui`, `platform` or `runtime` is Android code, everything
 * else under `core`, `feature` and `app` is pure. A feature is `feature.<name>` with its sub-packages.
 */
class ArchitectureTest {

    /** Feature A's code may use feature B's pure packages. One line per edge, with the reason. */
    private val featureEdges: Set<Pair<String, String>> = setOf(
        "progress" to "workout", // the charts and the calendar are drawn from finished workouts
        "progress" to "timer", // and from finished timer sessions
        "share" to "workout", // what is shared is a finished workout
        "history" to "workout", // the history lists finished workouts and edits them
        "history" to "timer", // and finished timer sessions
        "home" to "workout", // home offers the next routine and shows the last workout and its weight changes
        "settings" to "workout", // settings edit the working weight of each exercise
        "settings" to "timer", // and the timer's voice and calls
    )

    /** Feature A's `ui` may embed feature B's `ui`. One line per edge, with the reason. */
    private val uiEdges: Set<Pair<String, String>> = setOf(
        "history" to "progress", // the History tab shows the progress charts as its second view
        "history" to "share", // a workout in the history can be shared (ShareWorkoutSheet)
        "history" to "workout", // a workout in the history uses the workout screen's card shape and set descriptions
        "home" to "share", // the last workout can be shared from home
        "home" to "food", // home shows today's food as a card with Scan and Add (FoodTodayCard)
        "settings" to "health", // settings show the Health Connect rows
        "settings" to "timer", // settings embed the timer's voice controls (TimerCallsControls)
        "timer" to "workout", // the WOD builder picks its movements with the exercise picker sheet
    )

    @Test fun `the importer sees the app`() {
        assertTrue("only ${app.size} classes imported", app.size > 300)
        assertTrue("no feature classes imported", app.any { it.packageName.startsWith("$APP.feature.") })
    }

    @Test fun `1 core depends on nothing else of the app, core ui only on core`() {
        noClasses().that(inPackage("are in core") { it == "$APP.core" })
            .should().dependOnClassesThat(ofApp("are in any other package of the app") { it != "$APP.core" })
            .because("core is the kernel every feature uses: if it knew a feature, that feature could not be changed alone")
            .check(app)
        noClasses().that(inPackage("are in core.ui") { it == "$APP.core.ui" })
            .should().dependOnClassesThat(ofApp("are in a package of the app other than core and core.ui") { it != "$APP.core" && it != "$APP.core.ui" })
            .because("the shared Compose kit draws numbers and names for every feature and must not pull one of them in")
            .check(app)
    }

    @Test fun `2 pure code touches no Android, no IO and no ui, platform or runtime package`() {
        noClasses().that(inPackage("are pure (core, feature, app without ui, platform, runtime)", ::isPure))
            .should().dependOnClassesThat(
                inPackage("are in a ui, platform or runtime package") { pkg -> isApp(pkg) && kind(pkg) != null }
                    .or(
                        DescribedPredicate.describe<JavaClass>("are in android.., androidx.., java.io.. or java.net..") { type ->
                            // The Compose compiler annotates every class of the module with this, pure or not. It is no call into Android.
                            type.name != "androidx.compose.runtime.internal.StabilityInferred" &&
                                listOf("android", "androidx", "java.io", "java.net").any { type.packageName == it || type.packageName.startsWith("$it.") }
                        },
                    ),
            )
            .because("pure code is tested by sending it values: Android, files and the network are Cmds the runtime carries out")
            .check(app)
    }

    @Test fun `3 a feature's pure code uses core and the features the table allows, never app`() {
        classes().that(inPackage("are in a pure package of a feature") { isPure(it) && featureOf(it) != null })
            .should(onlyReach("only core, its own feature and the features in featureEdges") { from, to ->
                to == "$APP.core" || (isPure(to) && featureAllowed(featureOf(from), featureOf(to)))
            })
            .because("a feature that reaches into another one can no longer be changed or deleted alone; the program (app) knows the features, not the other way round")
            .check(app)
    }

    @Test fun `4 a feature's ui uses its own feature, core, the program and only the allowed parts of other features`() {
        classes().that(inPackage("are in the ui of a feature") { kind(it) == "ui" && featureOf(it) != null })
            .should(onlyReach("its own feature, core, core.ui, app.elm, app.state, other features' pure packages in featureEdges and their ui in uiEdges") { from, to ->
                val mine = featureOf(from)
                val theirs = featureOf(to)
                when {
                    to in setOf("$APP.core", "$APP.core.ui", "$APP.app.elm", "$APP.app.state") -> true
                    theirs == null -> false
                    theirs == mine -> true
                    isPure(to) -> featureAllowed(mine, theirs)
                    kind(to) == "ui" -> (mine to theirs) in uiEdges
                    else -> false
                }
            })
            .because("a screen is a view of the model: it sends Msgs and never drives another feature's Android side; embedding another feature's screen is a decision, so it is listed")
            .check(app)
    }

    @Test fun `5 only app elm and app state know every feature`() {
        noClasses().that(inPackage("are pure, outside app.elm and app.state") { isPure(it) && it != "$APP.app.elm" && it != "$APP.app.state" })
            .should().dependOnClassesThat(inPackage("are in app") { it == "$APP.app" || it.startsWith("$APP.app.") })
            .because("the Model, Msg, update and the saved state are the one place that ties the features together")
            .check(app)
    }

    @Test fun `6 features are free of cycles`() {
        slices().matching("$APP.feature.(*)..").should().beFreeOfCycles()
            .because("two features that need each other are one feature")
            .check(app)
    }

    @Test fun `every allowed edge is still used`() {
        val used = app.flatMap { type ->
            type.directDependenciesFromSelf.mapNotNull { dependency ->
                val from = featureOf(type.packageName)
                val target = dependency.targetClass.packageName
                val to = featureOf(target)
                if (from == null || to == null || from == to) null else Triple(from, to, kind(target) == "ui")
            }
        }.toSet()
        val stale = featureEdges.filter { (from, to) -> Triple(from, to, false) !in used } +
            uiEdges.filter { (from, to) -> Triple(from, to, true) !in used }
        assertTrue("these allowed edges are no longer used, remove them: $stale", stale.isEmpty())
    }

    private fun featureAllowed(from: String?, to: String?): Boolean =
        from != null && to != null && (from == to || (from to to) in featureEdges)

    private fun onlyReach(description: String, allowed: (from: String, to: String) -> Boolean) =
        object : ArchCondition<JavaClass>("only depend on $description") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                for (dependency in item.directDependenciesFromSelf) {
                    val target = dependency.targetClass.packageName
                    if (!isApp(target) || isGenerated(dependency.targetClass) || target == item.packageName) continue
                    if (!allowed(item.packageName, target)) events.add(SimpleConditionEvent.violated(dependency, dependency.description))
                }
            }
        }

    companion object {
        private const val APP = "io.github.alexanderop.eisen"

        /**
         * The production classes, imported once. The Android plugin hands them to unit tests as a jar, so jars
         * stay in; the tests themselves compile to a `...UnitTest` directory and are left out.
         */
        private val app: JavaClasses = ClassFileImporter()
            .withImportOption { location -> !location.contains("UnitTest") }
            .importPackages(APP)

        private fun isApp(pkg: String) = pkg == APP || pkg.startsWith("$APP.")

        private fun segments(pkg: String) = pkg.removePrefix("$APP.").split('.')

        /** `ui`, `platform` or `runtime` for Android code, null for pure code. */
        private fun kind(pkg: String): String? = segments(pkg).firstOrNull { it == "ui" || it == "platform" || it == "runtime" }

        private fun isPure(pkg: String) = isApp(pkg) && segments(pkg).first() in setOf("core", "feature", "app") && kind(pkg) == null

        private fun featureOf(pkg: String): String? = segments(pkg).takeIf { it.size > 1 && it[0] == "feature" }?.get(1)

        /** `R` and `BuildConfig`, which the build writes into the root package. */
        private fun isGenerated(type: JavaClass) =
            type.packageName == APP && (type.name == "$APP.BuildConfig" || type.name == "$APP.R" || type.name.startsWith("$APP.R$"))

        private fun inPackage(description: String, test: (String) -> Boolean): DescribedPredicate<JavaClass> =
            DescribedPredicate.describe(description) { type -> test(type.packageName) }

        /** Classes of the app other than `R` and `BuildConfig`, in a package that passes [test]. */
        private fun ofApp(description: String, test: (String) -> Boolean): DescribedPredicate<JavaClass> =
            DescribedPredicate.describe(description) { type -> isApp(type.packageName) && !isGenerated(type) && test(type.packageName) }
    }
}
