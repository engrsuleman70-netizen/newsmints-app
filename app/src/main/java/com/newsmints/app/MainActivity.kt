package com.newsmints.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class NewsArticle(
    val id: Int,
    val category: String,
    val title: String,
    val summary: String,
    val timeAgo: String
)

class MainActivity : ComponentActivity() {

    // EXACT PRODUCTION ADMOB IDS - DO NOT MODIFY
    private val bannerAdId = "ca-app-pub-1022964552774338/7307952757"
    private val interstitialAdId = "ca-app-pub-1022964552774338/5145870026"
    private val rewardedAdId = "ca-app-pub-1022964552774338/7280032592"

    // WHATSAPP REDEMPTION TARGET
    private val redeemWhatsAppNumber = "923134470502"

    // Anti-Fraud & Policy Limits
    private val bonusCooldownMillis = 30000L // 30-Second Cooldown
    private val maxDailyBonusAds = 20       // Max 20 Rewarded Ads per day
    private val maxDailyCheckIns = 10       // Max 10 Check-Ins per day
    private val maxDailyReadingPoints = 15  // Max 15 Points from reading per day
    private val redeemPointsCost = 1000      // 1000 Points = 50 PKR

    // SharedPreferences Storage Keys
    private val prefsName = "newsmints_prefs"
    private val keyCoins = "coins"
    private val keyDailyReadCount = "daily_read_count"
    private val keyDailyNewsReads = "daily_news_reads"
    private val keyBonusAdCount = "bonus_ad_count"
    private val keyLastDate = "last_date"
    private val keyClickCounter = "checkin_clicks"
    private val keyLastBonusAdTime = "last_bonus_ad_time"

    private lateinit var prefs: SharedPreferences

    // Reactive Compose State Holders
    private val coinsState = mutableIntStateOf(0)
    private val dailyCheckInCountState = mutableIntStateOf(0)
    private val dailyNewsReadsState = mutableIntStateOf(0)
    private val bonusAdCountState = mutableIntStateOf(0)
    private val lastBonusAdTimeState = mutableLongStateOf(0L)
    private val newsArticlesState = mutableStateOf<List<NewsArticle>>(emptyList())

    // AdMob Controllers
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        // Initialize Google Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Check date rollover on launch
        checkAndResetDailyLimit()

        // Read persisted stats
        coinsState.intValue = prefs.getInt(keyCoins, 0)
        dailyCheckInCountState.intValue = prefs.getInt(keyDailyReadCount, 0)
        dailyNewsReadsState.intValue = prefs.getInt(keyDailyNewsReads, 0)
        bonusAdCountState.intValue = prefs.getInt(keyBonusAdCount, 0)
        lastBonusAdTimeState.longValue = prefs.getLong(keyLastBonusAdTime, 0L)

        // Preload Ads asynchronously
        loadInterstitialAd()
        loadRewardedAd()

        // 1. Immediately populate with curated fallback to guarantee no blank screen
        newsArticlesState.value = getCuratedNewsArticles()

        // 2. Fetch live Google News RSS feeds in background
        fetchLiveNewsFromRss()

        setContent {
            MaterialTheme {
                MainRewardsScreen(
                    coins = coinsState.intValue,
                    dailyCheckInCount = dailyCheckInCountState.intValue,
                    dailyNewsReads = dailyNewsReadsState.intValue,
                    bonusAdCount = bonusAdCountState.intValue,
                    lastBonusAdTime = lastBonusAdTimeState.longValue,
                    newsList = newsArticlesState.value,
                    bannerAdId = bannerAdId,
                    onDailyCheckInClick = { handleDailyCheckIn() },
                    onBonusAdClick = { handleWatchBonusAd() },
                    onReadNewsArticle = { handleReadNewsArticle() },
                    onRedeemSubmit = { enteredNumber -> handleRedeemRequest(enteredNumber) }
                )
            }
        }
    }

    // Dynamic Google News RSS Background Loader
    private fun fetchLiveNewsFromRss() {
        lifecycleScope.launch(Dispatchers.IO) {
            val liveArticles = mutableListOf<NewsArticle>()
            var nextId = 100

            val feeds = listOf(
                Pair("https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=en-PK&gl=PK&ceid=PK:en", "Tech & AI"),
                Pair("https://news.google.com/rss/search?q=cricket+pakistan&hl=en-PK&gl=PK&ceid=PK:en", "Cricket Updates"),
                Pair("https://news.google.com/rss/headlines/section/topic/SCIENCE?hl=en-PK&gl=PK&ceid=PK:en", "Science & Facts")
            )

            for ((feedUrl, category) in feeds) {
                try {
                    val parsed = parseGoogleNewsRss(feedUrl, category, nextId)
                    liveArticles.addAll(parsed)
                    nextId += parsed.size
                } catch (_: Exception) {
                    // Graceful per-feed fallback: retain existing curated items
                }
            }

            if (liveArticles.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    newsArticlesState.value = liveArticles
                }
            }
        }
    }

    private fun parseGoogleNewsRss(feedUrl: String, category: String, startId: Int): List<NewsArticle> {
        val results = mutableListOf<NewsArticle>()
        var currentId = startId
        val url = URL(feedUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/119.0")

        conn.inputStream.use { stream ->
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            var eventType = parser.eventType
            var insideItem = false
            var title: String? = null
            var description: String? = null
            var pubDate: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT && results.size < 4) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            insideItem = true
                            title = null
                            description = null
                            pubDate = null
                        } else if (insideItem) {
                            when (tagName.lowercase()) {
                                "title" -> title = parser.nextText()
                                "description" -> description = parser.nextText()
                                "pubdate" -> pubDate = parser.nextText()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            insideItem = false
                            if (!title.isNullOrBlank()) {
                                val cleanTitle = cleanHtmlText(title ?: "")
                                var cleanSummary = cleanHtmlText(description ?: "")

                                if (cleanSummary.length < 25) {
                                    cleanSummary = "$cleanTitle. Follow daily updates on Newsmints to stay informed on the latest trends and verified stories across this category."
                                }

                                val timeAgo = formatRssDate(pubDate)

                                results.add(
                                    NewsArticle(
                                        id = currentId++,
                                        category = category,
                                        title = cleanTitle,
                                        summary = cleanSummary,
                                        timeAgo = timeAgo
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return results
    }

    private fun cleanHtmlText(input: String): String {
        return input
            .replace(Regex("<.*?>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun formatRssDate(pubDateStr: String?): String {
        if (pubDateStr.isNullOrBlank()) return "Recent"
        return try {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            val date = sdf.parse(pubDateStr) ?: return "Recent"
            val diffMs = System.currentTimeMillis() - date.time
            val hours = (diffMs / (1000 * 60 * 60)).toInt()
            when {
                hours <= 0 -> "Just now"
                hours < 24 -> "${hours}h ago"
                else -> "${hours / 24}d ago"
            }
        } catch (_: Exception) {
            "Recent"
        }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun checkAndResetDailyLimit() {
        val today = getTodayDateString()
        val lastDate = prefs.getString(keyLastDate, "")
        if (today != lastDate) {
            prefs.edit()
                .putString(keyLastDate, today)
                .putInt(keyDailyReadCount, 0)
                .putInt(keyDailyNewsReads, 0)
                .putInt(keyBonusAdCount, 0)
                .apply()
            dailyCheckInCountState.intValue = 0
            dailyNewsReadsState.intValue = 0
            bonusAdCountState.intValue = 0
        }
    }

    // TASK 1: DAILY CHECK-IN (+5 Points, capped strictly at 10 times daily)
    private fun handleDailyCheckIn() {
        checkAndResetDailyLimit()

        val currentDailyCount = prefs.getInt(keyDailyReadCount, 0)
        if (currentDailyCount >= maxDailyCheckIns) {
            Toast.makeText(
                this,
                "Daily check-in limit reached! Read articles or watch Bonus Ads for more Points",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val updatedCoins = prefs.getInt(keyCoins, 0) + 5
        val updatedDailyCount = currentDailyCount + 1
        val updatedClicks = prefs.getInt(keyClickCounter, 0) + 1

        prefs.edit()
            .putInt(keyCoins, updatedCoins)
            .putInt(keyDailyReadCount, updatedDailyCount)
            .putInt(keyClickCounter, updatedClicks)
            .apply()

        coinsState.intValue = updatedCoins
        dailyCheckInCountState.intValue = updatedDailyCount

        Toast.makeText(this, "+5 Points Added", Toast.LENGTH_SHORT).show()

        // Every 3 successful check-in clicks trigger the Interstitial Ad
        if (updatedClicks % 3 == 0) {
            showInterstitialAd()
        }
    }

    // TASK 2: BONUS AD (+10 Points with 30s Cooldown & 20 Daily Limit)
    private fun handleWatchBonusAd() {
        checkAndResetDailyLimit()

        val currentBonusCount = prefs.getInt(keyBonusAdCount, 0)
        if (currentBonusCount >= maxDailyBonusAds) {
            Toast.makeText(
                this,
                "Daily Bonus limit reached ($maxDailyBonusAds/$maxDailyBonusAds)! Try again tomorrow",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val lastTime = prefs.getLong(keyLastBonusAdTime, 0L)
        val elapsed = System.currentTimeMillis() - lastTime
        if (elapsed < bonusCooldownMillis) {
            val remainingSec = ((bonusCooldownMillis - elapsed) / 1000L).toInt() + 1
            Toast.makeText(
                this,
                "Please wait ${remainingSec}s before next Bonus Ad",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    loadRewardedAd()
                }
            }

            ad.show(this) { _ ->
                // Anti-fraud: Points credited ONLY inside verified Google callback
                val now = System.currentTimeMillis()
                val currentCoins = prefs.getInt(keyCoins, 0)
                val newCoins = currentCoins + 10
                val newBonusCount = prefs.getInt(keyBonusAdCount, 0) + 1

                prefs.edit()
                    .putInt(keyCoins, newCoins)
                    .putInt(keyBonusAdCount, newBonusCount)
                    .putLong(keyLastBonusAdTime, now)
                    .apply()

                coinsState.intValue = newCoins
                bonusAdCountState.intValue = newBonusCount
                lastBonusAdTimeState.longValue = now

                Toast.makeText(this@MainActivity, "+10 Bonus Points Added!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                this,
                "Bonus Ad is loading, please try again in a few seconds...",
                Toast.LENGTH_SHORT
            ).show()
            loadRewardedAd()
        }
    }

    // TASK 3: ARTICLE READING ENGAGEMENT (+1 Point per read, max 15 points/day)
    private fun handleReadNewsArticle() {
        checkAndResetDailyLimit()

        val currentReads = prefs.getInt(keyDailyNewsReads, 0)
        if (currentReads < maxDailyReadingPoints) {
            val updatedReads = currentReads + 1
            val updatedCoins = prefs.getInt(keyCoins, 0) + 1

            prefs.edit()
                .putInt(keyDailyNewsReads, updatedReads)
                .putInt(keyCoins, updatedCoins)
                .apply()

            coinsState.intValue = updatedCoins
            dailyNewsReadsState.intValue = updatedReads

            Toast.makeText(this, "+1 Point Added for Reading", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Daily reading reward limit reached (15/15 Points)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // TASK 4: REDEEM REQUEST (1000 Points = 50 PKR via WhatsApp)
    private fun handleRedeemRequest(enteredNumber: String): Boolean {
        val cleanNumber = enteredNumber.trim()
        if (cleanNumber.isEmpty()) {
            Toast.makeText(this, "Please enter your mobile account number", Toast.LENGTH_SHORT).show()
            return false
        }

        val currentCoins = prefs.getInt(keyCoins, 0)
        if (currentCoins < redeemPointsCost) {
            Toast.makeText(this, "Need $redeemPointsCost Points to Redeem", Toast.LENGTH_SHORT).show()
            return false
        }

        // Deduct 1000 points immediately
        val remainingCoins = currentCoins - redeemPointsCost
        prefs.edit().putInt(keyCoins, remainingCoins).apply()
        coinsState.intValue = remainingCoins

        // Retrieve ANDROID_ID
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        val txnId = "NM-" + UUID.randomUUID().toString().take(8).uppercase()

        // Construct verified transaction dispatch message
        val message = "NEW REDEEM REQUEST\nTxn ID: $txnId\nPoints: 1000 = 50 PKR\nJazzCash: $cleanNumber\nDevice: $androidId\nTime: ${System.currentTimeMillis()}"
        val waUrl = "https://wa.me/$redeemWhatsAppNumber?text=" + Uri.encode(message)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                startActivity(browserIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "WhatsApp not installed on device", Toast.LENGTH_SHORT).show()
            }
        }

        Toast.makeText(
            this,
            "Request Sent to 03134470502! You will get payment in 24 hours",
            Toast.LENGTH_LONG
        ).show()

        return true
    }

    // AdMob Interstitial Controller
    private fun loadInterstitialAd() {
        if (isInterstitialLoading || interstitialAd != null) return
        isInterstitialLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            }
        )
    }

    private fun showInterstitialAd() {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitialAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadInterstitialAd()
                }
            }
            ad.show(this)
        } else {
            loadInterstitialAd()
        }
    }

    // AdMob Rewarded Controller
    private fun loadRewardedAd() {
        if (isRewardedLoading || rewardedAd != null) return
        isRewardedLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            this,
            rewardedAdId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                }
            }
        )
    }

    // Policy-Safe Curated News Database (Offline Guaranteed Fallback)
    private fun getCuratedNewsArticles(): List<NewsArticle> {
        return listOf(
            NewsArticle(
                id = 1,
                category = "Tech & AI",
                title = "Next-Gen AI Models Boost Mobile Device Intelligence",
                summary = "Modern smartphones are now integrating dedicated neural processing units that handle machine learning models directly on the device. This on-device architecture reduces latency and provides stronger privacy protection for daily mobile computing tasks. Industry researchers predict mobile edge AI will soon become a default standard across all consumer electronics.",
                timeAgo = "2h ago"
            ),
            NewsArticle(
                id = 2,
                category = "Cricket Updates",
                title = "National Squad Initiates High-Altitude Conditioning Camp",
                summary = "The national cricket squad has convened for an intensive conditioning camp focused on stamina, fielding agility, and fitness metrics. Head coaches emphasized tailored workout routines designed to sustain peak physical performance throughout the busy international calendar. Players expressed high confidence and team unity during opening media interactions.",
                timeAgo = "3h ago"
            ),
            NewsArticle(
                id = 3,
                category = "Science & Facts",
                title = "Deep Ocean Robotic Probe Discovers New Bio-Luminescent Coral",
                summary = "Marine biologists deploying an autonomous submersible into the Pacific depths have identified vibrant coral colonies glowing with neon green proteins. The natural bioluminescence serves as a protective mechanism against ultraviolet light in deep ocean trenches. The finding opens novel pathways for medical imaging and biochemical sensor research.",
                timeAgo = "4h ago"
            ),
            NewsArticle(
                id = 4,
                category = "Tech & AI",
                title = "Breakthrough Solid-State Battery Tech Reaches Testing Phase",
                summary = "Materials scientists have developed a new ceramic electrolyte that significantly increases energy density in rechargeable batteries. The technology allows devices to charge twice as fast while substantially reducing overheating risks under continuous loads. Consumer testing is slated to commence by the end of next year across portable electronics.",
                timeAgo = "5h ago"
            ),
            NewsArticle(
                id = 5,
                category = "Cricket Updates",
                title = "Gaddafi Stadium Renovation Enters Final Inspection Phase",
                summary = "The ongoing renovation of Gaddafi Stadium has entered its final inspection phase featuring upgraded LED floodlights and enhanced spectator seating. Officials confirmed that ticketing and security infrastructure now adhere to top-tier international standards. Cricket enthusiasts are eagerly anticipating the grand reopening for the upcoming winter fixtures.",
                timeAgo = "6h ago"
            ),
            NewsArticle(
                id = 6,
                category = "Science & Facts",
                title = "Fascinating Fact: Honey Found in Ancient Pyramids Remains Edible",
                summary = "Archaeologists excavating ancient Egyptian tombs have uncovered pots of preserved honey over 3,000 years old that are still completely edible. Honey's remarkably low moisture content and natural acidity create a hostile environment for bacterial reproduction. This makes honey one of the only natural foods on Earth that virtually never spoils.",
                timeAgo = "7h ago"
            ),
            NewsArticle(
                id = 7,
                category = "Cricket Updates",
                title = "Rising Fast Bowler Impresses Selectors in Domestic Trophy",
                summary = "A nineteen-year-old pacer has garnered national acclaim after delivering match-winning spells in the domestic championship. His consistent 145 kph pace and disciplined yorkers drew praise from former legendary fast bowlers across the cricketing community. National selectors are reportedly monitoring his trajectory closely for upcoming bilateral tours.",
                timeAgo = "8h ago"
            ),
            NewsArticle(
                id = 8,
                category = "Science & Facts",
                title = "Global Reforestation Initiative Reaches 50 Million Trees Milestone",
                summary = "An international consortium of environmental organizations has successfully planted fifty million native trees across degraded arid landscapes. Satellite surveillance confirms a 25% recovery in soil moisture levels and local biodiversity within pilot zones. Project leaders aim to double the target acreage over the next three seasons.",
                timeAgo = "10h ago"
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRewardsScreen(
    coins: Int,
    dailyCheckInCount: Int,
    dailyNewsReads: Int,
    bonusAdCount: Int,
    lastBonusAdTime: Long,
    newsList: List<NewsArticle>,
    bannerAdId: String,
    onDailyCheckInClick: () -> Unit,
    onBonusAdClick: () -> Unit,
    onReadNewsArticle: () -> Unit,
    onRedeemSubmit: (String) -> Boolean
) {
    var showRedeemDialog by remember { mutableStateOf(false) }
    var selectedArticle by remember { mutableStateOf<NewsArticle?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var accountNumber by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val remainingCheckInLimit = (10 - dailyCheckInCount).coerceAtLeast(0)
    val remainingBonusLimit = (20 - bonusAdCount).coerceAtLeast(0)
    val remainingNewsPoints = (15 - dailyNewsReads).coerceAtLeast(0)

    // Calculate Dynamic Cooldown Countdown
    var cooldownRemainingSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(lastBonusAdTime) {
        while (true) {
            val elapsed = System.currentTimeMillis() - lastBonusAdTime
            val remainingMs = 30000L - elapsed
            if (remainingMs > 0) {
                cooldownRemainingSec = (remainingMs / 1000L).toInt() + 1
                delay(500L)
            } else {
                cooldownRemainingSec = 0
                break
            }
        }
    }

    val categories = listOf("All", "Tech & AI", "Cricket Updates", "Science & Facts")
    val filteredNews = if (selectedCategory == "All") newsList else newsList.filter { it.category == selectedCategory }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "Points",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "My Rewards: $coins Points",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        bottomBar = {
            // Pinned Bottom Banner Ad
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color(0xFFF1F5F9))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            adUnitId = bannerAdId
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Titles
                Text(
                    text = "Welcome to Newsmints",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Read daily updates and complete simple tasks for rewards",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                // Stats Row 1: Check-in & Reading Limits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (remainingCheckInLimit > 0) Color(0xFF2563EB) else Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Check-In", fontSize = 13.sp, color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$remainingCheckInLimit/10 left",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (remainingCheckInLimit > 0) Color(0xFF0F172A) else Color(0xFFDC2626)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = if (remainingNewsPoints > 0) Color(0xFF0D9488) else Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Reading", fontSize = 13.sp, color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$remainingNewsPoints/15 left",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (remainingNewsPoints > 0) Color(0xFF0F172A) else Color(0xFFDC2626)
                            )
                        }
                    }
                }

                // Stats Row 2: Bonus Ads (30s Cooldown) & Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF5FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9D5FF)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Bonus Ads (30s)", fontSize = 12.sp, color = Color(0xFF6B21A8))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$remainingBonusLimit/20 left",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (remainingBonusLimit > 0) Color(0xFF581C87) else Color(0xFFDC2626)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CurrencyExchange,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Exchange", fontSize = 13.sp, color = Color(0xFF047857))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1000 = 50 PKR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF065F46)
                            )
                        }
                    }
                }

                // Action Tasks Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Task 1 Button: Daily Check-In
                    Button(
                        onClick = onDailyCheckInClick,
                        enabled = remainingCheckInLimit > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            disabledContainerColor = Color(0xFFE2E8F0),
                            disabledContentColor = Color(0xFF94A3B8)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (remainingCheckInLimit > 0) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (remainingCheckInLimit > 0) "Daily Check-In +5 Points" else "Daily Check-In Complete",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Task 2 Button: Watch Bonus Ad with 30s Cooldown
                    val isBonusEnabled = remainingBonusLimit > 0 && cooldownRemainingSec == 0
                    val bonusButtonText = when {
                        remainingBonusLimit <= 0 -> "Bonus Limit Reached (20/20)"
                        cooldownRemainingSec > 0 -> "Cooldown Active (${cooldownRemainingSec}s)"
                        else -> "Watch Bonus Ad +10 Points"
                    }

                    Button(
                        onClick = onBonusAdClick,
                        enabled = isBonusEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C3AED),
                            disabledContainerColor = Color(0xFFE2E8F0),
                            disabledContentColor = Color(0xFF94A3B8)
                        )
                    ) {
                        Icon(
                            imageVector = if (cooldownRemainingSec > 0) Icons.Default.Timer else Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = if (isBonusEnabled) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bonusButtonText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Task 4: Redeem Button (1000 Points Threshold)
                    if (coins >= 1000) {
                        Button(
                            onClick = { showRedeemDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Redeem 1000 Points - Get 50 PKR",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text(
                                text = "Need 1000 Points to Redeem",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Curated News Feed Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Article,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Daily News & Updates",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Text(
                        text = "+1 Point / Read",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF059669)
                    )
                }

                // Category Chips Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(text = cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0F172A),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Color(0xFF475569)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)
                            )
                        )
                    }
                }

                // News Articles List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    filteredNews.forEach { article ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedArticle = article
                                    onReadNewsArticle()
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (article.category) {
                                                    "Tech & AI" -> Color(0xFFEFF6FF)
                                                    "Cricket Updates" -> Color(0xFFECFDF5)
                                                    else -> Color(0xFFFAF5FF)
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = article.category,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (article.category) {
                                                "Tech & AI" -> Color(0xFF1D4ED8)
                                                "Cricket Updates" -> Color(0xFF047857)
                                                else -> Color(0xFF7C3AED)
                                            }
                                        )
                                    }
                                    Text(
                                        text = article.timeAgo,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = article.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = article.summary,
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Tap to read full summary",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2563EB)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(+1 Point)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Article Reader Dialog (Summary)
    selectedArticle?.let { article ->
        AlertDialog(
            onDismissRequest = { selectedArticle = null },
            title = {
                Column {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = article.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = article.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = article.summary,
                        fontSize = 14.sp,
                        color = Color(0xFF334155),
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFECFDF5))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reward verified: +1 Point credited",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedArticle = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done Reading")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Modal Redeem Dialog
    if (showRedeemDialog) {
        AlertDialog(
            onDismissRequest = { showRedeemDialog = false },
            title = {
                Text(
                    text = "Redeem Reward",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter JazzCash / Easypaisa Number e.g. 0300XXXXXXX",
                        fontSize = 14.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0300XXXXXXX", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF059669),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = onRedeemSubmit(accountNumber)
                        if (success) {
                            accountNumber = ""
                            showRedeemDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Submit Request", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRedeemDialog = false }
                ) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        )
    }
}
