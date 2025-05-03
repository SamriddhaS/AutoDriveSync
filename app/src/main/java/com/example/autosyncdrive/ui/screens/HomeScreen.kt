package com.example.autosyncdrive.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(modifier: Modifier = Modifier, navigateTo:(route:String)->Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {

        Button(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally)
            , onClick = {

            }
        ) {
            Text(text = "Connect to google drive.")
        }

    }
}