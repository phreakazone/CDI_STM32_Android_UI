package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ComponentPinout
import com.example.ui.theme.*

@Composable
fun ComponentPinoutCard(
  component: ComponentPinout,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = TechSurfaceElevated),
    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(OutlineDark, SparkAmber.copy(alpha = 0.4f))))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = component.ref,
            style = MaterialTheme.typography.titleLarge,
            color = SparkAmber,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = component.name,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimaryDark,
            fontWeight = FontWeight.Medium
          )
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = ElectricCyan.copy(alpha = 0.12f),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ElectricCyan, ElectricCyanMuted)))
        ) {
          Text(
            text = component.packageType,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ElectricCyan,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Rating Spec badge
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0F1824),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(OutlineDark, OutlineDark))),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Rating / Spesifikasi: ",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark
          )
          Text(
            text = component.ratingSpec,
            style = MaterialTheme.typography.labelSmall,
            color = SafetyGreen,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Pinout Legs List
      Text(
        text = "URUTAN KAKI & PIN FISIK:",
        style = MaterialTheme.typography.labelSmall,
        color = ElectricCyan,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
      )

      Spacer(modifier = Modifier.height(6.dp))

      Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        component.pinLegs.forEach { leg ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF0C1420))
              .padding(8.dp),
            verticalAlignment = Alignment.Top
          ) {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = SparkAmber.copy(alpha = 0.2f),
              border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(SparkAmber, SparkAmberDark)))
            ) {
              Text(
                text = leg.pinNumber,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = SparkAmber,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
              Text(
                text = leg.name,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = leg.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSecondaryDark
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Orientation Guide
      Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF152233)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Panduan Orientasi",
            tint = ElectricCyan,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = component.orientationGuide,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = TextPrimaryDark
          )
        }
      }

      // Safety Notice if present
      if (component.safetyNotice != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
          shape = RoundedCornerShape(8.dp),
          colors = CardDefaults.cardColors(containerColor = HighVoltageRed.copy(alpha = 0.12f)),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(HighVoltageRed, HighVoltageOrange))),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Peringatan",
              tint = HighVoltageRed,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = component.safetyNotice,
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
              color = HighVoltageRed,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      // PSU Donor Rule
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Aturan Sourcing: ${component.donorPsuRule}",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        color = TextTertiaryDark
      )
    }
  }
}
