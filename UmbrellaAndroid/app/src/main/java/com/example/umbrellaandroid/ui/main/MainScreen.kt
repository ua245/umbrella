package com.example.umbrellaandroid.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.umbrellaandroid.data.DefaultDataRepository
import com.example.umbrellaandroid.theme.UmbrellaAndroidTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  when (state) {
    MainScreenUiState.Loading -> {
      // Blank
    }
    is MainScreenUiState.Success -> {
      MainScreen(data = (state as MainScreenUiState.Success).data, modifier = modifier)
    }
    is MainScreenUiState.Error -> {
      Text("Error loading data: ${(state as MainScreenUiState.Error).throwable.message}")
    }
  }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Umbrella App", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { /* TODO */ }) {
        Icon(Icons.Filled.Add, contentDescription = "Add")
      }
    },
    modifier = modifier.fillMaxSize()
  ) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
      Text(
        text = "Welcome to Umbrella",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(16.dp))
      data.forEach { GreetingCard(it) }
    }
  }
}

@Composable
fun GreetingCard(name: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier.padding(vertical = 8.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Text(
      text = "Hello $name!",
      modifier = Modifier.padding(16.dp),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  UmbrellaAndroidTheme {
    Column(modifier = Modifier.padding(16.dp)) {
      GreetingCard("Android")
    }
  }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
fun MainScreenPortraitPreview() {
  UmbrellaAndroidTheme {
    Column(modifier = Modifier.padding(16.dp)) {
      GreetingCard("Android")
    }
  }
}
