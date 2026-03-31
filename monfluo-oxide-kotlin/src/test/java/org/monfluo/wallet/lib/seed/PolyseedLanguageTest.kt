package org.monfluo.wallet.lib.seed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.monfluo.wallet.lib.LibMonfluoOxideExtension
import java.util.stream.Stream

private const val EXPECTED_WORD_COUNT: Int = 2048

@ExtendWith(LibMonfluoOxideExtension::class)
class PolyseedLanguageTest {
    @ParameterizedTest
    @MethodSource("polyseedLanguages")
    fun `polyseed language should have correct words`(
        language: PolyseedLanguage,
        firstWord: String,
        lastWord: String,
    ) {
        var words = language.getWords()
        assertEquals(EXPECTED_WORD_COUNT, words.size)
        assertEquals(firstWord, words[0])
        assertEquals(lastWord, words[words.size - 1])
    }

    companion object {
        @JvmStatic
        fun polyseedLanguages(): Stream<Arguments> =
            Stream.of(
                Arguments.of(PolyseedLanguage.English, "abandon", "zoo"),
                Arguments.of(PolyseedLanguage.Spanish, "ábaco", "zurdo"),
                Arguments.of(PolyseedLanguage.French, "abaisser", "zoologie"),
                Arguments.of(PolyseedLanguage.Italian, "abaco", "zuppa"),
                Arguments.of(PolyseedLanguage.Japanese, "あいこくしん", "われる"),
                Arguments.of(PolyseedLanguage.Korean, "가격", "힘껏"),
                Arguments.of(PolyseedLanguage.Czech, "abdikace", "zvyk"),
                Arguments.of(PolyseedLanguage.Portuguese, "abacate", "zumbido"),
                Arguments.of(PolyseedLanguage.ChineseSimplified, "的", "歇"),
                Arguments.of(PolyseedLanguage.ChineseTraditional, "的", "歇"),
            )
    }
}
