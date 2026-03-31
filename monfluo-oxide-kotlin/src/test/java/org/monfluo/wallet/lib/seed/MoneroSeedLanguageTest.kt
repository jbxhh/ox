package org.monfluo.wallet.lib.seed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.monfluo.wallet.lib.LibMonfluoOxideExtension
import java.util.stream.Stream

private const val EXPECTED_WORD_COUNT: Int = 1626

@ExtendWith(LibMonfluoOxideExtension::class)
class MoneroSeedLanguageTest {
    @ParameterizedTest
    @MethodSource("moneroSeedLanguages")
    fun `monero seed language should have correct words`(
        language: MoneroSeedLanguage,
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
        fun moneroSeedLanguages(): Stream<Arguments> =
            Stream.of(
                Arguments.of(MoneroSeedLanguage.Chinese, "的", "貌"),
                Arguments.of(MoneroSeedLanguage.English, "abbey", "zoom"),
                Arguments.of(MoneroSeedLanguage.Dutch, "aalglad", "zworen"),
                Arguments.of(MoneroSeedLanguage.Spanish, "ábaco", "rito"),
                Arguments.of(MoneroSeedLanguage.German, "Abakus", "Zyklop"),
                Arguments.of(MoneroSeedLanguage.Italian, "abbinare", "zucchero"),
                Arguments.of(MoneroSeedLanguage.Portuguese, "abaular", "zumbi"),
                Arguments.of(MoneroSeedLanguage.Japanese, "あいこくしん", "ひしょ"),
                Arguments.of(MoneroSeedLanguage.Russian, "абажур", "ящик"),
                Arguments.of(MoneroSeedLanguage.Esperanto, "abako", "zumilo"),
                Arguments.of(MoneroSeedLanguage.Lojban, "backi", "snaxa'a"),
                Arguments.of(MoneroSeedLanguage.DeprecatedEnglish, "like", "weary"),
            )
    }
}
