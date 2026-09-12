package org.example.project.presentation.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import org.example.project.presentation.components.*
import org.example.project.presentation.theme.UkraineBlue
import org.example.project.presentation.theme.UkraineYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedScreen(
    viewModel: NewsFeedViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Listen to one-off ViewModel side effects
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is NewsFeedEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is NewsFeedEffect.OpenUrlInBrowser -> {
                    // Open browser url action
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Global Pulse",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "News",
                            fontWeight = FontWeight.Light,
                            fontSize = 20.sp,
                            color = UkraineBlue
                        )
                    }
                },
                actions = {
                    // Search Action Button
                    IconButton(
                        onClick = { viewModel.handleIntent(NewsFeedIntent.ToggleSearch(!state.isSearchActive)) }
                    ) {
                        Text(if (state.isSearchActive) "✕" else "🔍", fontSize = 16.sp)
                    }

                    // Refresh Action Button
                    IconButton(
                        onClick = { viewModel.handleIntent(NewsFeedIntent.RefreshFeed) }
                    ) {
                        Text("🔄", fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Input Field (when active)
            if (state.isSearchActive) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.handleIntent(NewsFeedIntent.UpdateSearchQuery(it)) },
                    placeholder = { Text("Search stories by keyword, city, or topic...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UkraineBlue,
                        cursorColor = UkraineBlue
                    )
                )
            }

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.selectedFilter == FeedFilter.ALL,
                    onClick = { viewModel.handleIntent(NewsFeedIntent.SelectFilter(FeedFilter.ALL)) },
                    label = { Text("All Sources") }
                )
                FilterChip(
                    selected = state.selectedFilter == FeedFilter.UKRAINE_ONLY,
                    onClick = { viewModel.handleIntent(NewsFeedIntent.SelectFilter(FeedFilter.UKRAINE_ONLY)) },
                    label = { Text("🇺🇦 Ukraine (${state.ukraineArticlesCount})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UkraineBlue,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = state.selectedFilter == FeedFilter.WORLD,
                    onClick = { viewModel.handleIntent(NewsFeedIntent.SelectFilter(FeedFilter.WORLD)) },
                    label = { Text("World") }
                )
                FilterChip(
                    selected = state.selectedFilter == FeedFilter.BOOKMARKS,
                    onClick = { viewModel.handleIntent(NewsFeedIntent.SelectFilter(FeedFilter.BOOKMARKS)) },
                    label = { Text("★ Saved (${state.bookmarkedCount})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UkraineYellow,
                        selectedLabelColor = UkraineBlue
                    )
                )
            }

            // Ukraine Priority Spotlight Banner
            if (state.ukraineArticlesCount > 0 && state.selectedFilter != FeedFilter.WORLD && state.selectedFilter != FeedFilter.BOOKMARKS) {
                UkraineBanner(totalUkraineNews = state.ukraineArticlesCount)
            }

            // Main Feed Area
            when {
                state.isLoading && state.articles.isEmpty() -> {
                    LoadingView()
                }

                state.error != null && state.articles.isEmpty() -> {
                    ErrorView(
                        message = state.error ?: "Unable to fetch news feeds.",
                        onRetry = { viewModel.handleIntent(NewsFeedIntent.RefreshFeed) }
                    )
                }

                state.articles.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.selectedFilter == FeedFilter.BOOKMARKS) {
                                "No bookmarked articles yet. Tap the star on any story to save it!"
                            } else {
                                "No news articles found for current filter."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = state.articles,
                            key = { it.id }
                        ) { article ->
                            ArticleCard(
                                article = article,
                                onClick = { viewModel.handleIntent(NewsFeedIntent.SelectArticleForDetail(article)) },
                                onToggleBookmark = { viewModel.handleIntent(NewsFeedIntent.ToggleBookmark(article)) },
                                onDelete = { viewModel.handleIntent(NewsFeedIntent.DeleteArticle(article.id)) }
                            )
                        }
                    }
                }
            }
        }

        // Article Full Details Dialog
        state.selectedArticleForDetail?.let { article ->
            ArticleDetailDialog(
                article = article,
                onDismiss = { viewModel.handleIntent(NewsFeedIntent.SelectArticleForDetail(null)) },
                onToggleBookmark = { viewModel.handleIntent(NewsFeedIntent.ToggleBookmark(article)) },
                onDelete = { viewModel.handleIntent(NewsFeedIntent.DeleteArticle(article.id)) }
            )
        }
    }
}
