package com.example.widgetapp

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.widgetapp.databinding.ActivityMainBinding
import com.example.widgetapp.quiz.AnswerType
import com.example.widgetapp.quiz.QuizQuestion
import com.example.widgetapp.quiz.QuizUiState
import com.example.widgetapp.quiz.QuizViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: QuizViewModel by lazy {
        ViewModelProvider(this)[QuizViewModel::class.java]
    }
    private lateinit var swipeDetector: GestureDetector
    private var lastState = QuizUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.submitButton.setOnClickListener { submitAnswer() }
        binding.retryButton.setOnClickListener { viewModel.retry() }
        configureSwipe()
        observeQuiz()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::swipeDetector.isInitialized) swipeDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    private fun observeQuiz() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    lastState = state
                    render(state)
                }
            }
        }
    }

    private fun render(state: QuizUiState) = with(binding) {
        val contentVisible = state.question != null
        questionCard.isVisible = contentVisible
        choicesGroup.isVisible = contentVisible
        submitButton.isVisible = contentVisible && state.result == null && !state.isChecking
        loadingState.isVisible = state.isLoading || state.isChecking
        loadingMessage.setText(if (state.isChecking) R.string.checking_answer else R.string.loading_question)
        errorState.isVisible = state.errorMessage != null
        errorText.text = state.errorMessage.orEmpty()

        state.question?.let { question ->
            subjectLabel.text = question.subject.label
            questionText.text = question.prompt
            renderAnswerInput(
                question = question,
                userAnswer = state.userAnswer,
                answered = state.result != null || state.isChecking,
            )
        }

        state.result?.let { result ->
            explanationCard.isVisible = true
            resultTitle.text = result.title
            explanationText.text = result.explanation
            swipeHint.text = getString(if (state.isNextReady) R.string.swipe_next_ready else R.string.swipe_next_loading)
        } ?: run { explanationCard.isVisible = false }
    }

    private fun renderAnswerInput(
        question: QuizQuestion,
        userAnswer: String,
        answered: Boolean,
    ) = with(binding) {
        choicesGroup.removeAllViews()
        textAnswerLayout.isVisible = question.answerType == AnswerType.TEXT
        if (question.answerType == AnswerType.CHOICES) {
            question.choices.forEach { choice ->
                val option = RadioButton(this@MainActivity).apply {
                    id = View.generateViewId()
                    text = choice
                    textSize = 17f
                    setPadding(10, 12, 10, 12)
                    isEnabled = !answered
                    isChecked = choice == userAnswer
                }
                choicesGroup.addView(option)
            }
        }
        textAnswerEdit.isEnabled = !answered
        if (textAnswerEdit.text?.toString() != userAnswer) {
            textAnswerEdit.setText(userAnswer)
        }
    }

    private fun submitAnswer() = with(binding) {
        val question = lastState.question ?: return
        val answer = when (question.answerType) {
            AnswerType.CHOICES -> choicesGroup.findViewById<RadioButton>(choicesGroup.checkedRadioButtonId)?.text?.toString()
            AnswerType.TEXT -> textAnswerEdit.text?.toString()
        }
        if (answer.isNullOrBlank()) {
            Snackbar.make(quizPage, if (question.answerType == AnswerType.CHOICES) R.string.choose_an_answer else R.string.enter_an_answer, Snackbar.LENGTH_SHORT).show()
            return
        }
        viewModel.submitAnswer(answer)
    }

    private fun configureSwipe() {
        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onFling(start: MotionEvent?, end: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val from = start ?: return false
                val distanceX = end.x - from.x
                if (lastState.result != null && distanceX < -120 && kotlin.math.abs(distanceX) > kotlin.math.abs(end.y - from.y)) {
                    animateToNextQuestion()
                    return true
                }
                return false
            }
        })
    }

    private fun animateToNextQuestion() {
        binding.quizPage.animate()
            .translationX(-binding.quizPage.width.toFloat() * 0.16f)
            .alpha(0.2f)
            .setDuration(150)
            .withEndAction {
                viewModel.moveToNextQuestion()
                binding.quizScroll.scrollTo(0, 0)
                binding.quizPage.translationX = binding.quizPage.width.toFloat() * 0.16f
                binding.quizPage.animate().translationX(0f).alpha(1f).setDuration(220).start()
            }
            .start()
    }
}