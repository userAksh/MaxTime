package com.maxtime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.UserLocation
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes

/**
 * Bottom sheet shown once per session the first time Home renders — governed by
 * `state.locationConfirmed`, not a first-launch-ever flag (Section 5).
 */
@Composable
fun LocationPopup(state: AppState) {
    var manualArea by remember { mutableStateOf(state.location.area) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB2030402))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(MaxTimeColors.bg1)
                .border(1.dp, MaxTimeColors.cardBorder, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaxTimeColors.accentWash)
                    .border(2.dp, MaxTimeColors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("◆", color = MaxTimeColors.accent, fontSize = 22.sp) }

            Text(
                "Let's help you find people and slots",
                color = MaxTimeColors.textStrong,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
            )
            Text(
                "Tell us where you are so we can show nearby activities, open slots, and people up for the same thing.",
                color = MaxTimeColors.muted2,
                fontSize = 12.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            PrimaryButton(
                "◆ Use current location",
                onClick = {
                    state.location = UserLocation(area = "Hauz Khas, South Delhi", radiusKm = state.location.radiusKm)
                    state.locationConfirmed = true
                }
            )

            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text("or enter manually", color = MaxTimeColors.muted, fontSize = 11.sp)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaxTimeShapes.medium)
                    .background(MaxTimeColors.cardBg)
                    .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                BasicTextField(
                    value = manualArea,
                    onValueChange = { manualArea = it },
                    textStyle = TextStyle(color = MaxTimeColors.textStrong, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(MaxTimeColors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(MaxTimeShapes.medium)
                    .background(MaxTimeColors.cardBg)
                    .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) { Text("🗺 Select on map instead", color = MaxTimeColors.muted2, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }

            Box(Modifier.padding(top = 14.dp)) {
                PrimaryButton(
                    "Confirm location",
                    onClick = {
                        if (manualArea.isNotBlank()) {
                            state.location = UserLocation(area = manualArea.trim(), radiusKm = state.location.radiusKm)
                        }
                        state.locationConfirmed = true
                    }
                )
            }

            Text(
                "Skip for now",
                color = MaxTimeColors.muted,
                fontSize = 11.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable { state.locationConfirmed = true }
            )
        }
    }
}
