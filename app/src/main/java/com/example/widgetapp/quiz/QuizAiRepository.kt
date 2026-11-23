package com.example.widgetapp.quiz

import com.example.widgetapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import kotlin.text.isNotBlank

/** Contract for the AI gateway. Keep provider secrets on a server for production builds. */
interface QuizAiRepository {
    suspend fun generateQuestion(subject: Subject, recentPrompts: List<String>): QuizQuestion
}

/** Retrofit declaration for an OpenAI-compatible Chat Completions endpoint. */
private interface OpenAiCompatibleService {
    @POST("chat/completions")
    suspend fun createChatCompletion(@Body request: ChatCompletionRequest): Response<ChatCompletionResponse>
}

private data class ChatCompletionRequest(
    val model: String,
    val temperature: Double,
    val response_format: ResponseFormat,
    val messages: List<ChatMessage>,
)

private data class ResponseFormat(val type: String)
private data class ChatMessage(val role: String, val content: String)
private data class ChatCompletionResponse(val choices: List<ChatChoice>?)
private data class ChatChoice(val message: ChatMessage?)

/** Calls the configured AI gateway through Retrofit and a shared OkHttp client. */
class OpenAiCompatibleQuizAiRepository : QuizAiRepository {
    private val service: OpenAiCompatibleService = createService()
    override suspend fun generateQuestion(
        subject: Subject,
        recentPrompts: List<String>,
    ): QuizQuestion = withContext(Dispatchers.IO) {
        val content = requestCompletion(
            systemPrompt = """
                你是一位严谨、友善的小学教师。请只生成一题适合中国小学 1 到 6 年级的${subject.label}题。
                题干简洁、没有歧义，内容必须安全、适龄。题型要在选择题、填空题、计算题、简短阅读/应用题中尽量变化。
                只能返回 JSON 对象，字段固定为：subject（${subject.name}）、prompt、answerType（CHOICES/TEXT）、choices（数组；TEXT 时为空数组）、correctAnswer、explanation。
                CHOICES 必须提供 4 个选项，correctAnswer 必须与其中一个选项完全一致。explanation 要用小学生能理解的话分步骤讲解，不能泄露与题目无关的内容。
            """.trimIndent(),
            userPrompt = """
                请生成一道与下面最近题目在主题、题型、数值或文本上明显不同的新题：
                ${recentPrompts.ifEmpty { listOf("暂无历史题目") }.joinToString("\n")}
            """.trimIndent(),
        )
        parseQuestion(content)
    }

    private suspend fun requestCompletion(systemPrompt: String, userPrompt: String): String {
        check(BuildConfig.QUIZ_AI_API_KEY.isNotBlank()) {
            "尚未配置 AI 密钥，请在 local.properties 中填写 QUIZ_AI_API_KEY。"
        }
        val response = service.createChatCompletion(
            ChatCompletionRequest(
                model = BuildConfig.QUIZ_AI_MODEL,
                temperature = 0.7,
                response_format = ResponseFormat(type = "json_object"),
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userPrompt),
                ),
            ),
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("AI 服务请求失败（${response.code()}）：${response.errorBody()?.string().orEmpty().take(200)}")
        }
        return response.body()
            ?.choices
            ?.firstOrNull()
            ?.message
            ?.content
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("AI 服务没有返回可用内容。")
    }

    private fun parseQuestion(content: String): QuizQuestion {
        val json = JSONObject(content)
        val answerType = AnswerType.valueOf(json.getString("answerType"))
        val choices = json.optJSONArray("choices")?.toStringList().orEmpty()
        val correctAnswer = json.getString("correctAnswer").trim()
        require(json.getString("prompt").isNotBlank()) { "AI 返回的题目为空。" }
        require(correctAnswer.isNotBlank()) { "AI 返回的答案为空。" }
        require(answerType != AnswerType.CHOICES || choices.size == 4 && choices.contains(correctAnswer)) {
            "AI 返回的选择题格式无效。"
        }
        return QuizQuestion(
            id = System.nanoTime(),
            subject = Subject.valueOf(json.getString("subject")),
            prompt = json.getString("prompt").trim(),
            answerType = answerType,
            choices = choices,
            correctAnswer = correctAnswer,
            explanation = json.getString("explanation").trim(),
        )
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index).trim())
    }

    private companion object {
        fun createService(): OpenAiCompatibleService {
            val authInterceptor = Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer ${BuildConfig.QUIZ_AI_API_KEY}")
                        .header("Content-Type", "application/json")
                        .build(),
                )
            }
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                // Never log headers or bodies: the Authorization header contains a secret.
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .build()
            return Retrofit.Builder()
                .baseUrl(BuildConfig.QUIZ_AI_BASE_URL.trimEnd('/') + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiCompatibleService::class.java)
        }
    }
}
