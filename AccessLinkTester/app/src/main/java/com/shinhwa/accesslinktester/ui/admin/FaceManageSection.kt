package com.shinhwa.accesslinktester.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shinhwa.accesslinktester.AccessLinkAppController
import com.shinhwa.accesslinktester.ui.components.InfoCard
import com.shinhwa.accesslinktester.ui.components.SectionLabel
import com.shinhwa.accesslinktester.ui.face.FaceCameraView
import com.shinhwa.accesslinktester.ui.face.FaceDetectionState

@Composable
fun FaceManageSection(controller: AccessLinkAppController) {
    var faceState by remember { mutableStateOf(FaceDetectionState()) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("FACE · 얼굴 등록")

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("전면 카메라", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "얼굴 비교 모델 연결 전까지는 등록 정보만 저장하며, 이 등록만으로 문은 열지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FaceCameraView(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    enableRecognition = true,
                    onFaceState = { faceState = it }
                )
                Text(
                    "${faceState.message} · ${faceState.faceCount}명",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error,
                    label = { Text("등록자 이름") }
                )
                Button(
                    onClick = {
                        val ok = controller.registerFace(name, faceState.embedding)
                        if (ok) {
                            name = ""
                        } else {
                            error = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = faceState.faceCount == 1 && faceState.embedding != null
                ) {
                    Text("얼굴 등록")
                }
            }
        }

        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "등록 얼굴 (${controller.faces.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (controller.faces.isEmpty()) {
                    Text(
                        "등록된 얼굴이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    controller.faces.forEach { face ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(face.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "얼굴 비교 템플릿 저장됨",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { controller.removeFace(face.id) }) {
                                    Text("삭제")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
