package be.tolly.core.model

import java.text.Normalizer

/** A colour for a course, as a hue; [saturation] scales the theme's usual saturation (brown, grey). */
enum class CourseColor(val hue: Float, val saturation: Float = 1f) {
    RED(4f),
    ORANGE(24f),
    AMBER(42f),
    LIME(80f),
    GREEN(135f),
    TEAL(172f),
    CYAN(192f),
    BLUE(214f),
    INDIGO(236f),
    PURPLE(272f),
    PINK(326f),
    BROWN(24f, saturation = 0.45f),
    GREY(214f, saturation = 0.12f),
}

/** The picture on a course's avatar. [INITIALS] shows letters; the others a subject icon in [defaultColor]. */
enum class CourseIcon(val defaultColor: CourseColor?) {
    INITIALS(null),
    MATH(CourseColor.BLUE),
    STATISTICS(CourseColor.INDIGO),
    CODE(CourseColor.GREEN),
    TOOLS(CourseColor.TEAL),
    DATABASE(CourseColor.AMBER),
    LOGIC(CourseColor.PURPLE),
    AI(CourseColor.PINK),
    GRAPHICS(CourseColor.PURPLE),
    HCI(CourseColor.CYAN),
    WEB(CourseColor.BLUE),
    NETWORK(CourseColor.CYAN),
    CLOUD(CourseColor.CYAN),
    SECURITY(CourseColor.RED),
    ELECTRONICS(CourseColor.ORANGE),
    ROBOTICS(CourseColor.ORANGE),
    ENERGY(CourseColor.AMBER),
    PHYSICS(CourseColor.INDIGO),
    ASTRONOMY(CourseColor.INDIGO),
    CHEMISTRY(CourseColor.LIME),
    BIOLOGY(CourseColor.GREEN),
    ENVIRONMENT(CourseColor.GREEN),
    AGRICULTURE(CourseColor.LIME),
    MEDICINE(CourseColor.RED),
    PHARMACY(CourseColor.TEAL),
    ECONOMICS(CourseColor.AMBER),
    LAW(CourseColor.BROWN),
    POLITICS(CourseColor.BLUE),
    SOCIOLOGY(CourseColor.ORANGE),
    EDUCATION(CourseColor.TEAL),
    LANGUAGE(CourseColor.CYAN),
    MEDIA(CourseColor.RED),
    LITERATURE(CourseColor.PURPLE),
    HISTORY(CourseColor.BROWN),
    PHILOSOPHY(CourseColor.PURPLE),
    THEOLOGY(CourseColor.BROWN),
    PSYCHOLOGY(CourseColor.PINK),
    ENGINEERING(CourseColor.ORANGE),
    ARCHITECTURE(CourseColor.TEAL),
    GEOGRAPHY(CourseColor.GREEN),
    ART(CourseColor.PINK),
    PHOTOGRAPHY(CourseColor.GREY),
    MUSIC(CourseColor.PURPLE),
    SPORTS(CourseColor.LIME),
    LIBRARY(CourseColor.BROWN),
    PROJECT(CourseColor.ORANGE),
    THESIS(CourseColor.INDIGO),
    INTERNSHIP(CourseColor.BROWN),
    SEMINAR(CourseColor.PINK),
    LAB(CourseColor.LIME),
    CALCULATOR(CourseColor.GREY),
    COMMUNITY(CourseColor.GREY),
    BOOK(CourseColor.BLUE),
}

/** What the user changed about a course. Null fields mean "automatic". */
data class CourseCustomization(
    val nickname: String? = null,
    val icon: CourseIcon? = null,
    val color: CourseColor? = null,
)

/** Guesses a subject icon from a course name (Dutch or English), so "Wiskunde I" gets the maths icon. */
object CourseIcons {
    // Checked in order, so more specific subjects ("biochemie", "statistiek") come before broad ones.
    private val keywords: List<Pair<CourseIcon, List<String>>> = listOf(
        // What kind of course it is says more than its subject: "Bachelorproef informatica" is a
        // thesis, "Didactiek van de wiskunde" is about teaching.
        CourseIcon.THESIS to listOf("bachelorproef", "masterproef", "thesis", "eindwerk", "scriptie", "dissertation"),
        CourseIcon.INTERNSHIP to listOf(" stage", "internship", "werkplekleren"),
        CourseIcon.PROJECT to listOf("project", "ontwerpopdracht", "capstone"),
        CourseIcon.SEMINAR to listOf("seminar", "colloqui", "werkcollege"),
        CourseIcon.LAB to listOf("practicum", "labo ", "laboratori", " lab "),
        CourseIcon.EDUCATION to listOf("onderwijs", "pedagog", "didactiek", "didactic", "education", "teaching", "lerarenopleiding", "opvoedkunde"),
        CourseIcon.AI to listOf("artificiele intelligentie", "artificial intelligence", "machine learning", "deep learning", "neura", "kunstmatige"),
        CourseIcon.GRAPHICS to listOf(
            "computergrafiek", "computer graphics", "grafische", "visualisatie", "visualization", "visualisation",
            "beeldverwerking", "image processing", "computervisie", "computer vision", "game ",
        ),
        CourseIcon.HCI to listOf("mens-machine", "mens-computer", "human-computer", "gebruikersinterface", "user interface", "user experience", " ux ", "usability", "interactieontwerp"),
        CourseIcon.WEB to listOf("webtechnolog", "webontwikkeling", "web development", "web technolog", " web ", "webapplicat", "web application"),
        CourseIcon.STATISTICS to listOf("statistiek", "statistic", "kansrekening", "probabilit", "data-analyse", "data analysis", "econometri", "data science", "kansreken", "methodolog", "onderzoeksmethod", "research method"),
        CourseIcon.DATABASE to listOf("gegevensbank", "database", "databank", "sql", "informatiesysteem", "information system", "big data"),
        CourseIcon.LOGIC to listOf("logica", "logic", "bewijzen", "redeneren", "proof", "formele methode", "automaten", "automata", "berekenbaarheid", "computability"),
        CourseIcon.TOOLS to listOf("werktuigen", "tools", "besturingssysteem", "operating system", "linux"),
        CourseIcon.SECURITY to listOf("beveiliging", "security", "cryptograf", "cryptograph", "privacy"),
        CourseIcon.CLOUD to listOf("gedistribueerd", "distributed", "cloud", "parallel", "concurrency", "gelijktijdig"),
        CourseIcon.ELECTRONICS to listOf(
            "elektronica", "electronic", "elektrotechniek", "electrical", "schakeling", "circuit", "signal", "signaal",
            "computerarchitectuur", "computer architecture", "computersystemen", "computer systems", "digitale systemen", "hardware", "elektrisch",
        ),
        CourseIcon.NETWORK to listOf("netwerk", "network", "telecommunicatie", "internet"),
        CourseIcon.ROBOTICS to listOf("robot", "mechatronica", "mechatronics", "ingebedde", "embedded", "automatisering"),
        CourseIcon.CODE to listOf(
            "programmeren", "programming", "programmeer", "software", "algoritm", "datastructur",
            "data structures", "compiler", "objectgericht", "object-oriented", "informatica", "computer science", "coderen",
        ),
        CourseIcon.MATH to listOf(
            "wiskunde", "mathemat", "analyse", "analysis", "algebra", "calculus", "meetkunde", "geometr",
            "differentiaal", "differential", "getaltheorie", "number theory", "discrete", "lineaire", "linear",
            "numerieke", "numerical", "optimalisatie", "optimization", "optimisation", "operationeel onderzoek", "operations research",
        ),
        CourseIcon.ASTRONOMY to listOf("sterrenkunde", "astronom", "astrofysica", "astrophysic", "kosmologie", "cosmolog", "ruimtevaart", "aerospace"),
        CourseIcon.PHYSICS to listOf("fysica", "physics", "natuurkunde", "mechanica", "mechanics", "thermodynami", "kwantum", "quantum", "electromagnet", "elektromagnet", "optica", "optics", "magnetis"),
        CourseIcon.ENERGY to listOf("energie", "energy", "elektriciteit", "electricity", "power systems", "vermogen"),
        CourseIcon.PHARMACY to listOf("farmac", "pharma", "geneesmiddel", "toxicolog"),
        CourseIcon.BIOLOGY to listOf(
            "biochemie", "biochemistry", "biologie", "biology", "genetica", "genetics", "microbio", "celbiologie",
            "moleculair", "molecular", "botanie", "botany", "zoolog", "evolutie", "evolution", "biotechnolog", "planten", "plant ",
        ),
        CourseIcon.CHEMISTRY to listOf("chemie", "chemistry", "scheikunde", "chemisch"),
        CourseIcon.ENVIRONMENT to listOf("milieu", "environment", "duurzaam", "duurzaamheid", "sustainab", "klimaat", "climate", "ecologie", "ecology"),
        CourseIcon.AGRICULTURE to listOf("landbouw", "agricult", " voeding", "nutrition", "levensmiddel", "food", "bodem", "soil", "dierwetenschap", "bio-ingenieur"),
        CourseIcon.MEDICINE to listOf(
            "geneeskunde", "medicine", "medisch", "medical", "anatomie", "anatomy", "fysiologie", "physiology",
            "verpleeg", "nursing", "tandheelkunde", "dentist", "gezondheid", "health", "kinesitherap", "physiotherap", "revalidatie", "pathologie", "immunolog", "histolog", "embryolog", "epidemiolog", "klinisch", "clinical", "logopedie", "audiolog", "pathology",
        ),
        CourseIcon.PSYCHOLOGY to listOf("psycholog", "neurowetenschap", "neuroscience", "cognitie", "cognitive"),
        CourseIcon.THEOLOGY to listOf("theolog", "religie", "religion", "godsdienst", "bijbel", "bible", "spiritualiteit", "spirituality"),
        CourseIcon.PHILOSOPHY to listOf("filosofie", "wijsbegeerte", "philosoph", "ethiek", "ethics"),
        CourseIcon.HISTORY to listOf("geschiedenis", "history", "historisch", "archeolog", "archaeolog", "oudheid", "antiquity", "middeleeuw", "medieval"),
        CourseIcon.POLITICS to listOf(
            "politiek", "politic", "politicolog", "beleid", "policy", "internationale betrekkingen", "international relations",
            "europese unie", "european union", "bestuurskunde", "public administration", "diplomat",
        ),
        CourseIcon.LAW to listOf("recht", " law", "juridisch", "legal", "wetgeving", "fiscaliteit", "criminolog"),
        CourseIcon.SOCIOLOGY to listOf("sociolog", "sociale wetenschap", "social science", "antropolog", "anthropolog", "samenleving", "society", "demografie", "demograph", "gender", "sociaal", "social"),
        CourseIcon.ECONOMICS to listOf(
            "economie", "economic", "boekhoud", "accounting", "financ", "marketing", "management", "bedrijfs", "business",
            "ondernem", "entrepreneur", "belasting", "taxation", "bank", "verzekering", "insurance", "logistiek", "logistics", "accountan", "kostencalc", "organisatie", "organization", "organisation", "human resources",
        ),
        CourseIcon.MEDIA to listOf("journalistiek", "journalism", " media", "film", "televisie", "television", "reclame", "advertising", "communicatiewetenschap", "communication science"),
        CourseIcon.LITERATURE to listOf("literatuur", "literature", "letterkunde", "poezie", "poetry", "proza"),
        CourseIcon.LANGUAGE to listOf(
            " taal", "language", "engels", "english", "frans", "french", "duits", "german", "spaans", "spanish",
            "nederlands", "dutch", "italiaans", "italian", "chinees", "chinese", "japans", "japanese", "arabisch", "arabic",
            "russisch", "russian", "latijn", "latin", "grieks", "greek", "vertal", "translat", "tolk",
            "linguist", "communicatie", "communication", "academic writing", "schrijfvaardigheid", "grammatica", "grammar", "schrijven", "writing",
        ),
        CourseIcon.ARCHITECTURE to listOf("architectuur", "architecture", "stedenbouw", "urban", "bouwkunde"),
        CourseIcon.ENGINEERING to listOf("ingenieur", "engineering", "ontwerp", "design", "regeltechniek", "control systems", "constructie", "materiaalkunde", "materials", "werktuigkunde", "sterkteleer", "strength of materials"),
        CourseIcon.GEOGRAPHY to listOf("geografie", "geography", "aardrijkskunde", "geologie", "geology", "cartograf", "cartograph", "ruimtelijke", " gis ", "hydrolog", "aardwetenschap", "earth science"),
        CourseIcon.MUSIC to listOf("muziek", "music"),
        CourseIcon.PHOTOGRAPHY to listOf("fotografie", "photograph"),
        CourseIcon.ART to listOf("kunst", " art ", " arts ", "beeldende", "visual arts", "theater", "dans "),
        CourseIcon.SPORTS to listOf("sport", "beweging", "lichamelijke opvoeding", "physical education", "kinesiolog", "motori"),
        CourseIcon.LIBRARY to listOf("bibliothe", "library"),
        CourseIcon.CALCULATOR to listOf("oefenzitting", "oefeningen", "exercises", "rekenen"),
    )

    fun guess(name: String, kind: CourseKind): CourseIcon {
        val text = " " + normalize(name) + " "
        return keywords.firstOrNull { (_, words) -> words.any { it in text } }?.first
            ?: if (kind == CourseKind.COMMUNITY) CourseIcon.COMMUNITY else CourseIcon.INITIALS
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
