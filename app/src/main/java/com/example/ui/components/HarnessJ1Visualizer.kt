package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HarnessPin
import com.example.ui.theme.*

@Composable
fun HarnessJ1Visualizer(
  pins: List<HarnessPin>,
  selectedPinNumber: Int?,
  onSelectPin: (HarnessPin) -> Unit,
  modifier: Modifier = Modifier
) {
  val topRowPins = pins.filter { it.pinNumber in 1..6 }
  val bottomRowPins = pins.filter { it.pinNumber in 7..12 }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = TechSurfaceElevated),
    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(OutlineDark, ElectricCyan.copy(alpha = 0.4f))))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "MUKA SOKET HARNESS CDI J1 (NS200)",
            style = MaterialTheme.typography.labelLarge,
            color = ElectricCyan,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Tampak depan muka soket (Klip kait/latch di posisi atas)",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
          )
        }

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = SparkAmber.copy(alpha = 0.15f),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(SparkAmber, SparkAmberDark)))
        ) {
          Text(
            text = "12 PIN",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = SparkAmber,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Outer Socket Graphic Housing
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0F1723))
          .border(2.dp, Color(0xFF24364D), RoundedCornerShape(12.dp))
          .padding(12.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Top Latch Graphic
          Box(
            modifier = Modifier
              .width(84.dp)
              .height(10.dp)
              .background(SparkAmber.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
              .border(1.dp, SparkAmber, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "KAIT / LATCH ATAS",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
              color = Color.Black,
              fontWeight = FontWeight.Black
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Top Row (Pins 1 - 6)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            topRowPins.forEach { pin ->
              PinSocketItem(
                pin = pin,
                isSelected = pin.pinNumber == selectedPinNumber,
                onClick = { onSelectPin(pin) }
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Bottom Row (Pins 7 - 12)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            bottomRowPins.forEach { pin ->
              PinSocketItem(
                pin = pin,
                isSelected = pin.pinNumber == selectedPinNumber,
                onClick = { onSelectPin(pin) }
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Orientation Guidance Note
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = CircleShape,
          color = SparkAmber,
          modifier = Modifier.size(6.dp)
        ) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Baris Atas: Pin 1–6 (Kiri ke Kanan) | Baris Bawah: Pin 7–12 (Kiri ke Kanan)",
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = TextTertiaryDark
        )
      }
    }
  }
}

@Composable
private fun PinSocketItem(
  pin: HarnessPin,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val pinColor = when (pin.pinNumber) {
    1, 8, 9 -> Color(0xFF475569) // NC
    5 -> Color(0xFFE65100) // +12V kontak
    11 -> Color(0xFFFFD600) // GND (Kuning Hitam)
    10 -> Color(0xFF00E5FF) // Pulser (Cyan)
    12 -> Color(0xFFFF9100) // Coil Center (Orange)
    6 -> Color(0xFFFF5252) // Coil Side (Merah Hitam)
    7 -> Color(0xFF448AFF) // Fan (Biru Kuning)
    2, 4 -> Color(0xFF69F0AE) // TPS (Hijau Putih / Abu)
    3 -> Color(0xFFB388FF) // Temp (Hitam Putih)
    else -> ElectricCyan
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .width(44.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable { onClick() }
      .padding(vertical = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(
          if (isSelected) pinColor.copy(alpha = 0.35f) else Color(0xFF142030)
        )
        .border(
          width = if (isSelected) 2.5.dp else 1.5.dp,
          color = if (isSelected) ElectricCyan else pinColor.copy(alpha = 0.7f),
          shape = CircleShape
        ),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .size(14.dp)
          .clip(CircleShape)
          .background(pinColor)
      )
      Text(
        text = "${pin.pinNumber}",
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 9.sp,
          fontWeight = FontWeight.ExtraBold
        ),
        color = if (pinColor == Color(0xFFFFD600) || pinColor == Color(0xFF69F0AE)) Color.Black else Color.White
      )
    }

    Spacer(modifier = Modifier.height(3.dp))

    Text(
      text = "J1.${pin.pinNumber}",
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace
      ),
      color = if (isSelected) ElectricCyan else TextSecondaryDark,
      textAlign = TextAlign.Center
    )
  }
}
