package id.kasirq.dailysunnah.other

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.kasirq.dailysunnah.BuildConfig
import id.kasirq.dailysunnah.R
import id.kasirq.dailysunnah.ui.BackNavigationButton

private enum class OtherPage { MENU, SETTINGS, ABOUT, TERMS, PRIVACY }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OtherScreen(
    onOpenZakat: () -> Unit,
    themePreference: AppThemePreference,
    onThemePreferenceChanged: (AppThemePreference) -> Unit,
    languagePreference: AppLanguagePreference,
    onLanguagePreferenceChanged: (AppLanguagePreference) -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(OtherPage.MENU.name) }
    val page = OtherPage.valueOf(pageName)
    val english = languagePreference == AppLanguagePreference.ENGLISH
    fun back() { pageName = OtherPage.MENU.name }

    BackHandler(enabled = page != OtherPage.MENU, onBack = ::back)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(page.title(english)) },
                navigationIcon = { if (page != OtherPage.MENU) BackNavigationButton(onClick = ::back) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            when (page) {
                OtherPage.MENU -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuEntry(
                        "Z",
                        "Zakat",
                        if (english) "Zakat calculators and saved results" else "Kalkulator dan riwayat zakat",
                        image = true,
                        onClick = onOpenZakat,
                    )
                    MenuEntry("⚙", if (english) "Settings" else "Pengaturan", if (english) "Theme and language" else "Tema dan bahasa") { pageName = OtherPage.SETTINGS.name }
                    MenuEntry("i", "About Us", if (english) "About Daily Sunnah" else "Tentang Daily Sunnah") { pageName = OtherPage.ABOUT.name }
                    MenuEntry("§", if (english) "Terms of Service" else "Syarat layanan", if (english) "Terms for using this app" else "Ketentuan penggunaan aplikasi") { pageName = OtherPage.TERMS.name }
                    MenuEntry("◉", if (english) "Privacy Policy" else "Kebijakan privasi", if (english) "How app data is handled" else "Cara aplikasi mengelola data") { pageName = OtherPage.PRIVACY.name }
                }
                OtherPage.SETTINGS -> SettingsPage(
                    english = english,
                    themePreference = themePreference,
                    onThemePreferenceChanged = onThemePreferenceChanged,
                    languagePreference = languagePreference,
                    onLanguagePreferenceChanged = onLanguagePreferenceChanged,
                )
                OtherPage.ABOUT -> InformationPage(aboutSections(english))
                OtherPage.TERMS -> InformationPage(termsSections(english))
                OtherPage.PRIVACY -> InformationPage(privacySections(english))
            }
        }
    }
}

@Composable
private fun MenuEntry(icon: String, title: String, description: String, image: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (image) {
                Image(painterResource(R.drawable.tab_zakat), null, Modifier.size(48.dp))
            } else {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text(icon, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsPage(
    english: Boolean,
    themePreference: AppThemePreference,
    onThemePreferenceChanged: (AppThemePreference) -> Unit,
    languagePreference: AppLanguagePreference,
    onLanguagePreferenceChanged: (AppLanguagePreference) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (english) "App theme" else "Tema aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        AppThemePreference.entries.forEach { preference ->
            PreferenceRow(
                title = when (preference) {
                    AppThemePreference.SYSTEM -> if (english) "Follow device" else "Ikuti perangkat"
                    AppThemePreference.LIGHT -> if (english) "Light" else "Terang"
                    AppThemePreference.DARK -> if (english) "Dark" else "Gelap"
                },
                selected = preference == themePreference,
                onClick = { onThemePreferenceChanged(preference) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(if (english) "Language" else "Bahasa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        AppLanguagePreference.entries.forEach { preference ->
            PreferenceRow(
                title = when (preference) {
                    AppLanguagePreference.INDONESIAN -> if (english) "Indonesian" else "Bahasa Indonesia"
                    AppLanguagePreference.ENGLISH -> "English"
                },
                selected = preference == languagePreference,
                onClick = { onLanguagePreferenceChanged(preference) },
            )
        }
        Text(
            if (english) "The selected language applies across app menus and available content. Some source material, including Arabic text and proper names, remains in its original language."
            else "Bahasa pilihan berlaku untuk menu dan konten aplikasi yang tersedia. Sebagian materi sumber, termasuk teks Arab dan nama diri, tetap memakai bahasa aslinya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreferenceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun InformationPage(sections: List<Pair<String, String>>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sections.forEach { (title, body) ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun OtherPage.title(english: Boolean): String = when (this) {
    OtherPage.MENU -> if (english) "More" else "Lainnya"
    OtherPage.SETTINGS -> if (english) "Settings" else "Pengaturan"
    OtherPage.ABOUT -> "About Us"
    OtherPage.TERMS -> if (english) "Terms of Service" else "Syarat layanan"
    OtherPage.PRIVACY -> if (english) "Privacy Policy" else "Kebijakan privasi"
}

private fun aboutSections(english: Boolean) = if (english) listOf(
    "Daily Sunnah" to "A companion for Quran reading, worship routines, prayer times, and selected Islamic talks.",
    "Version" to BuildConfig.VERSION_NAME,
    "Content" to "Prayer times are calculated from your selected location and method. Quran translations, religious explanations, and talk videos are provided for learning; consult trusted scholars for personal religious rulings.",
) else listOf(
    "Daily Sunnah" to "Teman untuk membaca Al-Qur’an, rutinitas ibadah, jadwal shalat, dan kajian pilihan.",
    "Versi" to BuildConfig.VERSION_NAME,
    "Konten" to "Jadwal shalat dihitung berdasarkan lokasi dan metode yang dipilih. Terjemah, penjelasan agama, dan video kajian disediakan untuk belajar; konsultasikan hukum ibadah pribadi kepada ustaz atau ulama tepercaya.",
)

private fun termsSections(english: Boolean) = if (english) listOf(
    "Using the app" to "Use Daily Sunnah for personal, lawful, and respectful purposes. Do not interfere with the app or use it to distribute harmful or unlawful material.",
    "Religious content" to "Prayer times depend on location and calculation settings and can differ from local mosque schedules. Verify times with a trusted local authority. App content is educational and is not a personal fatwa.",
    "Third-party services" to "Talk videos are supplied through YouTube and may be subject to YouTube’s terms and availability. Network access may be required for online content.",
) else listOf(
    "Penggunaan aplikasi" to "Gunakan Daily Sunnah untuk keperluan pribadi yang sah dan saling menghormati. Jangan mengganggu aplikasi atau menggunakannya untuk menyebarkan materi berbahaya maupun melanggar hukum.",
    "Konten ibadah" to "Jadwal shalat bergantung pada lokasi dan pengaturan perhitungan, sehingga dapat berbeda dari jadwal masjid setempat. Cocokkan dengan otoritas lokal tepercaya. Konten aplikasi bersifat edukatif, bukan fatwa pribadi.",
    "Layanan pihak ketiga" to "Video kajian disediakan melalui YouTube dan mengikuti ketentuan serta ketersediaan layanan YouTube. Konten online memerlukan koneksi internet.",
)

private fun privacySections(english: Boolean) = if (english) listOf(
    "Data stored on your device" to "Bookmarks, reading goals, saved talks, app preferences, and prayer-location coordinates are stored locally on your device. Prayer reminders are scheduled by Android on your device.",
    "Online services" to "When you open the talk catalog, the app requests approved video metadata from Supabase. Video playback uses YouTube; Google/YouTube may process connection and device information under their own privacy terms.",
    "Location" to "Location permission is used to calculate prayer times for your selected location. Coordinates are saved locally and are not sent to the talk catalog service by this app.",
    "Managing data" to "You can remove locally stored app data by clearing Daily Sunnah’s storage or uninstalling the app. Internet and third-party service privacy practices are governed by their providers.",
) else listOf(
    "Data di perangkat" to "Bookmark, target tilawah, kajian tersimpan, preferensi aplikasi, dan koordinat lokasi shalat disimpan secara lokal di perangkat. Pengingat shalat dijadwalkan Android di perangkat.",
    "Layanan online" to "Saat membuka katalog kajian, aplikasi meminta metadata video yang telah disetujui dari Supabase. Pemutaran video menggunakan YouTube; Google/YouTube dapat memproses informasi koneksi dan perangkat sesuai kebijakan privasi mereka.",
    "Lokasi" to "Izin lokasi digunakan untuk menghitung jadwal shalat berdasarkan lokasi pilihan. Koordinat disimpan secara lokal dan tidak dikirim aplikasi ini ke layanan katalog kajian.",
    "Mengelola data" to "Hapus data lokal dengan menghapus penyimpanan Daily Sunnah atau mencopot pemasangan aplikasi. Praktik privasi koneksi internet dan layanan pihak ketiga mengikuti penyedia masing-masing.",
)
