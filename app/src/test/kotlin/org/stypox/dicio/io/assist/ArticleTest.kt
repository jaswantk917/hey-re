package org.stypox.dicio.io.assist

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jsoup.Jsoup

class ArticleTest : StringSpec({
    "articles are fetched" {
        Article.worthFetching("https://www.theverge.com/2026/10/30/pixel-10-review") shouldBe true
        Article.worthFetching("https://www.reddit.com/r/india/comments/abc123/some_post/") shouldBe true
        Article.worthFetching("https://en.wikipedia.org/wiki/Masala_chai") shouldBe true
    }

    "one-time links are never fetched behind the person's back" {
        Article.worthFetching("https://example.com/login?next=/home") shouldBe false
        Article.worthFetching("https://example.com/auth/magic?token=abc") shouldBe false
        Article.worthFetching("https://example.com/account/reset-password/xyz") shouldBe false
        Article.worthFetching("https://example.com/invite/abc123") shouldBe false
        Article.worthFetching("https://example.com/post/1?code=4321") shouldBe false
    }

    "homepages and searches have no one article" {
        Article.worthFetching("https://www.google.com/search?q=masala+chai") shouldBe false
        Article.worthFetching("https://duckduckgo.com/?q=masala+chai") shouldBe false
        Article.worthFetching("https://news.ycombinator.com/") shouldBe false
        Article.worthFetching("not a link") shouldBe false
    }

    "the article's paragraphs are read, not the menus around them" {
        val body = "Masala chai is spiced milk tea. ".repeat(20)
        val page = Article.read(
            Jsoup.parse(
                """<html><head><title>Chai</title><meta property="og:title" content="All about chai"></head>
                <body><nav><p>Home Menu Login</p></nav><article><p>$body</p></article><footer><p>Copyright</p></footer></body></html>"""
            )
        )
        page shouldNotBe null
        page!!.title shouldBe "All about chai"
        page.text shouldContain "spiced milk tea"
        page.text shouldNotContain "Menu"
        page.text shouldNotContain "Copyright"
    }

    "a sign-in wall is not an article" {
        Article.read(Jsoup.parse("<html><body><main><p>Sign in to continue</p></main></body></html>")) shouldBe null
    }
})
