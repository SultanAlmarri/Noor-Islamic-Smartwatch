package com.noor.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.prayers.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.Profiles

/** Plain statement of how accurate each profile is, with the evidence behind it. */
@Composable
fun AccuracyScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val ar = isArabicUi()
    val c = NoorTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.textPrimary) }
            Text(stringResource(R.string.about_accuracy), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        GlassCard(Modifier.fillMaxWidth(), highlighted = true) {
            Text(if (ar) Profiles.DUBAI_IACAD.nameAr else Profiles.DUBAI_IACAD.nameEn, style = MaterialTheme.typography.titleMedium, color = c.gold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (ar) "قورنت الأوقات بـ588 وقتًا رسميًا من الدائرة (أغسطس 2020 – نوفمبر 2026). تطابق 527 منها بالدقيقة (89.6%)، وكل الباقي بفارق دقيقة واحدة فقط. لم يُعدَّل أي يوم بعينه لفرض التطابق. يستمر الاختبار شهريًا مع كل جدول رسمي جديد."
                else "Checked against 588 official IACAD times (August 2020 – November 2026): 527 match to the minute (89.6%) and every other one is within 1 minute. No day was adjusted individually to force a match. Testing continues with every new official month.",
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            )
        }
        for (p in Profiles.ALL.drop(1)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(if (ar) p.nameAr else p.nameEn, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(if (ar) p.validationAr else p.validationEn, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
