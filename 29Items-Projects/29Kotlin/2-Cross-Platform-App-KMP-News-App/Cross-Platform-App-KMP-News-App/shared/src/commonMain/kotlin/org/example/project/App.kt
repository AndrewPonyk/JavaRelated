package org.example.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.example.project.data.local.ArticleDao
import org.example.project.data.remote.KtorNewsApi
import org.example.project.data.repository.NewsRepositoryImpl
import org.example.project.domain.usecase.GetUkraineNewsUseCase
import org.example.project.presentation.feed.NewsFeedScreen
import org.example.project.presentation.feed.NewsFeedViewModel
import org.example.project.presentation.theme.NewsAppTheme

@Composable
fun App() {
    // Production Service Locator / Dependency Graph Initialization
    val viewModel = remember {
        val newsApi = KtorNewsApi()
        val articleDao = ArticleDao()
        val repository = NewsRepositoryImpl(newsApi, articleDao)
        val useCase = GetUkraineNewsUseCase(repository)
        NewsFeedViewModel(useCase)
    }

    NewsAppTheme {
        NewsFeedScreen(viewModel = viewModel)
    }
}