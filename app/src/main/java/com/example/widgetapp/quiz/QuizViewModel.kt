package com.example.widgetapp.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuizViewModel(
    private val repository: QuizAiRepository = OpenAiCompatibleQuizAiRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val preloadedQuestions = ArrayDeque<QuizQuestion>()
    private val recentPrompts = ArrayDeque<String>()
    private var nextSubjectIndex = 0
    private var questionRequestId = 0L

    init {
        loadInitialBatch()
    }

    fun submitAnswer(answer: String) {
        val question = _uiState.value.question ?: return
        val normalizedAnswer = answer.trim()
        if (normalizedAnswer.isBlank() || _uiState.value.result != null) return

        val isCorrect = normalizedAnswer.equals(question.correctAnswer, ignoreCase = true)
        _uiState.value = _uiState.value.copy(
            userAnswer = normalizedAnswer,
            result = AnswerResult(
                isCorrect = isCorrect,
                title = if (isCorrect) "答对了，真棒！👍" else "再想一想，也没关系！",
                explanation = if (isCorrect) {
                    "继续保持，你的判断很准确！"
                } else {
                    "正确答案是“${question.correctAnswer}”。\n${question.explanation}"
                },
            ),
            errorMessage = null,
        )
        refillQuestionBuffer()
    }

    fun moveToNextQuestion() {
        if (_uiState.value.result == null) return
        val next = preloadedQuestions.removeFirstOrNull()
        if (next != null) {
            showQuestion(next)
            refillQuestionBuffer()
        } else {
            loadSingleQuestion()
        }
    }

    fun retry() {
        val state = _uiState.value
        if (state.question != null && state.userAnswer.isNotBlank() && state.result == null) {
            submitAnswer(state.userAnswer)
        } else if (state.question == null) {
            loadSingleQuestion()
        }
    }

    private fun loadInitialBatch() {
        val requestId = ++questionRequestId
        _uiState.value = QuizUiState(isLoading = true)
        viewModelScope.launch {
            val subjects = List(INITIAL_BATCH_SIZE) { nextSubject() }
            val results = subjects.map { subject ->
                async { runCatching { createQuestion(subject) } }
            }.awaitAll()
            if (requestId != questionRequestId) return@launch

            val questions = results.mapNotNull { it.getOrNull() }
            if (questions.isEmpty()) {
                _uiState.value = QuizUiState(
                    isLoading = false,
                    errorMessage = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
                        ?: "题目暂时加载失败，请重试。",
                )
                return@launch
            }
            questions.drop(1).forEach(preloadedQuestions::addLast)
            showQuestion(questions.first())
        }
    }

    private fun loadSingleQuestion() {
        val requestId = ++questionRequestId
        _uiState.value = QuizUiState(isLoading = true)
        viewModelScope.launch {
            runCatching { createQuestion(nextSubject()) }
                .onSuccess { question ->
                    if (requestId == questionRequestId) showQuestion(question)
                }
                .onFailure { error ->
                    if (requestId == questionRequestId) {
                        _uiState.value = QuizUiState(
                            isLoading = false,
                            errorMessage = error.message ?: "题目暂时加载失败，请重试。",
                        )
                    }
                }
        }
    }

    private fun refillQuestionBuffer() {
        if (preloadedQuestions.size >= PRELOAD_COUNT) {
            updateNextReady()
            return
        }
        viewModelScope.launch {
            while (preloadedQuestions.size < PRELOAD_COUNT) {
                runCatching { createQuestion(nextSubject()) }
                    .onSuccess(preloadedQuestions::addLast)
                    .onFailure { break }
            }
            updateNextReady()
        }
    }

    private suspend fun createQuestion(subject: Subject): QuizQuestion {
        val question = repository.generateQuestion(subject, recentPrompts.toList())
        rememberPrompt(question.prompt)
        return question
    }

    private fun showQuestion(question: QuizQuestion) {
        _uiState.value = QuizUiState(
            question = question,
            isLoading = false,
            isNextReady = preloadedQuestions.isNotEmpty(),
        )
    }

    private fun updateNextReady() {
        _uiState.value = _uiState.value.copy(isNextReady = preloadedQuestions.isNotEmpty())
    }

    private fun nextSubject(): Subject {
        val subject = Subject.entries[nextSubjectIndex % Subject.entries.size]
        nextSubjectIndex++
        return subject
    }

    private fun rememberPrompt(prompt: String) {
        recentPrompts.addLast(prompt)
        while (recentPrompts.size > RECENT_PROMPT_LIMIT) recentPrompts.removeFirst()
    }

    private companion object {
        /** Initial network batch: 1 question is shown and the other 9 are retained for paging. */
        const val INITIAL_BATCH_SIZE = 24
        const val PRELOAD_COUNT = INITIAL_BATCH_SIZE - 1
        const val RECENT_PROMPT_LIMIT = 12
    }
}
