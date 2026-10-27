package com.tripathitechnologies.melaready.domain

import java.util.UUID

data class Event(val name: String, val venue: String, val attendance: Int, val sample: Boolean = false)
data class Member(val id: String = UUID.randomUUID().toString(), val name: String, val role: String, val zone: String)
enum class IncidentStatus { SAVED, ACKNOWLEDGED, ASSIGNED, RESOLVED }
data class Incident(val id: String = UUID.randomUUID().toString(), val note: String, val zone: String,
    val created: Long = System.currentTimeMillis(), val status: IncidentStatus = IncidentStatus.SAVED)
data class Workspace(val event: Event? = null, val members: List<Member> = emptyList(),
    val answers: List<Int> = emptyList(), val checks: Set<Int> = emptySet(),
    val incidents: List<Incident> = emptyList(), val hindi: Boolean = false,
    val learning: Map<String, Int> = emptyMap(),
    val gatewayCompleted: Boolean = false,
    val serverUrl: String = "http://10.0.2.2:8080")
data class GeoFix(val latitude: Double, val longitude: Double, val accuracy: Float?, val capturedAt: Long,
    val elapsedNanos: Long)

object Workflow {
    fun validEvent(name: String, venue: String, attendance: String): Boolean =
        name.trim().length in 2..80 && venue.trim().length in 2..120 && (attendance.toIntOrNull() ?: 0) in 1..1_000_000
    fun validMember(name: String, role: String, zone: String): Boolean =
        name.trim().length in 2..60 && role.trim().length in 2..60 && zone.trim().length in 1..60
    fun next(status: IncidentStatus): IncidentStatus = when(status) {
        IncidentStatus.SAVED -> IncidentStatus.ACKNOWLEDGED
        IncidentStatus.ACKNOWLEDGED -> IncidentStatus.ASSIGNED
        IncidentStatus.ASSIGNED -> IncidentStatus.RESOLVED
        IncidentStatus.RESOLVED -> IncidentStatus.RESOLVED
    }
    fun sample(hindi: Boolean = false) = Workspace(
        event = Event("Riverfront volunteer drill", "Fictional venue · Gate A", 250, true),
        members = listOf(Member("V01", "Asha (sample)", "Team lead", "Control point"),
            Member("V02", "Dev (sample)", "Steward", "Gate A"),
            Member("V03", "Meera (sample)", "Volunteer", "Help desk")), hindi = hindi)
}

object Training {
    val correct = listOf(1, 2, 1)
    fun complete(answers: List<Int>) = answers == correct
}

data class LearningQuestion(val promptEn: String, val promptHi: String,
    val options: List<Pair<String, String>>, val correct: Int,
    val whyEn: String, val whyHi: String)
data class LearningTopic(val id: String, val titleEn: String, val titleHi: String,
    val subtitleEn: String, val subtitleHi: String, val questions: List<LearningQuestion>)

object LearningBank {
    private fun q(en:String, hi:String, a:Pair<String,String>, b:Pair<String,String>, c:Pair<String,String>, correct:Int, whyEn:String, whyHi:String) =
        LearningQuestion(en, hi, listOf(a,b,c), correct, whyEn, whyHi)
    val topics = listOf(
        LearningTopic("communication", "Clear communication", "स्पष्ट संचार", "Verify, assign and close the loop", "पुष्टि, जिम्मेदारी और फॉलो-अप", listOf(
            q("A message says Gate B is closed but gives no source. What first?", "बिना स्रोत के संदेश आया कि गेट B बंद है। पहले क्या करें?", "Forward it" to "आगे भेजें", "Ask the designated lead to verify" to "निर्धारित लीड से पुष्टि कराएँ", "Announce a route" to "नया रास्ता घोषित करें",1,"Verify before circulating an operational update.","काम का अपडेट फैलाने से पहले पुष्टि करें।"),
            q("Which report is easiest to act on?", "किस रिपोर्ट पर काम करना सबसे आसान है?", "Problem near here" to "यहाँ पास समस्या", "Crowd issue" to "भीड़ की समस्या", "Gate A, east queue, barrier loose, seen 10:20" to "गेट A, पूर्वी कतार, बैरियर ढीला, 10:20 पर देखा",2,"A place, observation and time make follow-up clear.","स्थान, अवलोकन और समय से फॉलो-अप स्पष्ट होता है।"),
            q("When is a report complete?", "रिपोर्ट कब पूरी मानी जाए?", "After it is sent" to "भेजते ही", "After someone reacts" to "किसी प्रतिक्रिया के बाद", "After ownership and outcome are recorded" to "जिम्मेदारी और परिणाम दर्ज होने पर",2,"Delivery is different from accountable completion.","संदेश पहुँचना और जिम्मेदारी से काम पूरा होना अलग हैं।"))),
        LearningTopic("reunion", "Reunion & privacy", "मिलन और गोपनीयता", "Help without exposing people", "निजी जानकारी उजागर किए बिना मदद", listOf(
            q("A child is separated from family. What should a volunteer do?", "बच्चा परिवार से बिछड़ गया है। स्वयंसेवक क्या करे?", "Post their photo publicly" to "फोटो सार्वजनिक डालें", "Follow the venue child-safeguarding and lost-person process" to "स्थान की बाल-सुरक्षा और खोया-व्यक्ति प्रक्रिया अपनाएँ", "Ask them to search alone" to "अकेले खोजने भेजें",1,"Use the designated safeguarding process.","निर्धारित सुरक्षा प्रक्रिया अपनाएँ।"),
            q("Who needs a volunteer's phone number?", "स्वयंसेवक का फोन नंबर किसे चाहिए?", "Everyone at the event" to "हर आगंतुक को", "Only authorized teammates when needed" to "ज़रूरत पर केवल अधिकृत टीम को", "Post it on social media" to "सोशल मीडिया पर डालें",1,"Limit contact details to the people who need them.","संपर्क जानकारी केवल जरूरत वाले लोगों तक रखें।"),
            q("What is safest in a public missing-person notice?", "सार्वजनिक खोया-व्यक्ति सूचना में क्या सुरक्षित है?", "All identity documents" to "सभी पहचान दस्तावेज", "Home address and Aadhaar" to "घर का पता और आधार", "Minimum verified details approved by the responsible lead" to "जिम्मेदार लीड द्वारा मंजूर न्यूनतम सत्यापित जानकारी",2,"Share the minimum approved information.","न्यूनतम मंजूर जानकारी साझा करें।"))),
        LearningTopic("venue", "Venue observation", "स्थान अवलोकन", "Report facts, not guesses", "तथ्य बताएँ, अनुमान नहीं", listOf(
            q("You notice an exit partly blocked. What do you report?", "निकास आंशिक रूप से बंद है। क्या रिपोर्ट करें?", "The event is unsafe" to "कार्यक्रम असुरक्षित है", "Exit 2 blocked by two boxes at 14:10" to "निकास 2 पर 14:10 बजे दो डिब्बे हैं", "Someone will fix it" to "कोई ठीक कर देगा",1,"Specific facts can be checked and assigned.","स्पष्ट तथ्य जाँचे और सौंपे जा सकते हैं।"),
            q("A map pin means what?", "नक्शे का पिन क्या बताता है?", "The route is safe" to "रास्ता सुरक्षित है", "An estimated point that needs context" to "अनुमानित स्थान जिसे संदर्भ चाहिए", "A rescue is coming" to "बचाव आ रहा है",1,"A coordinate is not a safety guarantee.","निर्देशांक सुरक्षा की गारंटी नहीं है।"),
            q("A location is 8 minutes old. What should you do?", "लोकेशन 8 मिनट पुरानी है। क्या करें?", "Call it live" to "इसे लाइव कहें", "Hide its age" to "उम्र छिपाएँ", "Refresh or clearly mark it stale" to "रीफ्रेश करें या पुरानी लिखें",2,"Age and accuracy belong with every location.","हर लोकेशन के साथ समय और सटीकता दिखाएँ।"))),
        LearningTopic("digital", "Digital safety", "डिजिटल सुरक्षा", "Handle links and rumours carefully", "लिंक और अफवाह सावधानी से संभालें", listOf(
            q("An unknown link claims to be an emergency form. What first?", "अनजान लिंक खुद को आपात फॉर्म बताता है। पहले क्या करें?", "Enter details" to "जानकारी भरें", "Verify the official domain/source" to "आधिकारिक डोमेन/स्रोत जाँचें", "Forward quickly" to "जल्दी आगे भेजें",1,"Check the source before entering or sharing information.","जानकारी देने या साझा करने से पहले स्रोत जाँचें।"),
            q("Which password practice is better?", "कौन-सी पासवर्ड आदत बेहतर है?", "One password everywhere" to "हर जगह एक पासवर्ड", "Share it with teammates" to "टीम से साझा करें", "Unique password and protected device" to "अलग पासवर्ड और सुरक्षित डिवाइस",2,"Accounts should not share reusable credentials.","खातों में साझा या दोहराए पासवर्ड न रखें।"),
            q("A dramatic crowd video has no date. What should the team do?", "भीड़ का वीडियो है पर तारीख नहीं। टीम क्या करे?", "Treat it as live" to "इसे लाइव मानें", "Verify time, place and source" to "समय, स्थान और स्रोत जाँचें", "Add a warning caption and send" to "चेतावनी लिखकर भेज दें",1,"Old or unrelated media can cause harmful decisions.","पुराना या असंबंधित मीडिया गलत निर्णय करा सकता है।"))),
        LearningTopic("inclusive", "Inclusive teamwork", "समावेशी टीमवर्क", "Communicate for different needs", "अलग जरूरतों के अनुसार संवाद", listOf(
            q("A visitor cannot hear an audio announcement. What helps?", "आगंतुक ऑडियो घोषणा नहीं सुन सकता। क्या मदद करेगा?", "Repeat louder only" to "सिर्फ तेज बोलें", "Use clear visual or written guidance too" to "स्पष्ट दृश्य या लिखित निर्देश भी दें", "Ignore it" to "अनदेखा करें",1,"Important information should use more than one mode.","जरूरी जानकारी एक से अधिक तरीके से दें।"),
            q("Who should receive the simplest instructions?", "सबसे सरल निर्देश किसे मिलने चाहिए?", "Only children" to "केवल बच्चों को", "Only new volunteers" to "केवल नए स्वयंसेवकों को", "Everyone benefits from clear short instructions" to "स्पष्ट छोटे निर्देश सबके लिए अच्छे हैं",2,"Plain language reduces confusion for everyone.","सरल भाषा सबका भ्रम कम करती है।"),
            q("A teammate says they cannot do a physical task. Best response?", "साथी शारीरिक काम नहीं कर सकता। सही प्रतिक्रिया?", "Remove them" to "उन्हें हटा दें", "Assign a suitable role and confirm support needs" to "उपयुक्त भूमिका दें और सहायता पूछें", "Question them publicly" to "सार्वजनिक सवाल करें",1,"Adapt roles without exposing private information.","निजी जानकारी उजागर किए बिना भूमिका अनुकूल करें।"))),
        LearningTopic("readiness", "Before-event readiness", "कार्यक्रम-पूर्व तैयारी", "Prepare roles, sources and backups", "भूमिका, स्रोत और बैकअप तैयार करें", listOf(
            q("What should be agreed before the event?", "कार्यक्रम से पहले क्या तय होना चाहिए?", "Only the uniform" to "केवल वर्दी", "Roles, zones and escalation path" to "भूमिकाएँ, क्षेत्र और एस्केलेशन रास्ता", "Social media captions" to "सोशल मीडिया कैप्शन",1,"People act faster when responsibility is clear.","जिम्मेदारी स्पष्ट हो तो लोग जल्दी काम करते हैं।"),
            q("What does ticking a permit checklist mean?", "परमिट चेकलिस्ट टिक करने का क्या अर्थ है?", "Approval is granted" to "मंजूरी मिल गई", "The app verified the law" to "ऐप ने कानून जाँच लिया", "Only that the organizer marked a planning item" to "केवल आयोजक ने तैयारी का बिंदु चिह्नित किया",2,"A checklist is not authority approval.","चेकलिस्ट प्राधिकरण की मंजूरी नहीं है।"),
            q("What is a useful backup for the demo?", "डेमो का उपयोगी बैकअप क्या है?", "A tested short video and sample data" to "जाँचा छोटा वीडियो और नमूना डेटा", "A promise that it works" to "काम करने का वादा", "More untested features" to "और बिना जाँचे फीचर",0,"A tested backup protects the presentation from device problems.","जाँचा बैकअप डिवाइस समस्या से प्रस्तुति बचाता है।")))
    )
    fun topic(id:String) = topics.first { it.id == id }
}
