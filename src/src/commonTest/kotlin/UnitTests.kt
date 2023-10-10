import kotlin.test.*

class CommonTest
{
    companion object
    {
        val sampleData = listOf(
          "123 abc",
          "abc 123",
          "123 ABC",
          "ABC 123"
        )
    }

    fun grep(lines: List<String>, pattern: String, action: (String) -> Unit)
    {
        val regex = pattern.toRegex()
        lines.filter(regex::containsMatchIn).forEach(action)
    }

    @Test
    fun shouldFindMatches()
    {
        val results = mutableListOf<String>()
        grep(sampleData, "[a-z]+") {
            results.add(it)
        }

        assertEquals(2, results.size)
        for (result in results) {
            assertContains(result, "abc")
        }
    }
}
