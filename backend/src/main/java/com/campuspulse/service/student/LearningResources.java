package com.campuspulse.service.student;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where to learn a skill: curated official documentation for common skills, plus search links
 * on well-known free learning sites. Search links always work; no deep links are invented.
 */
public final class LearningResources {

    public record Resource(String label, String url, String kind) {
    }

    private static final Map<String, String> OFFICIAL = Map.ofEntries(
            Map.entry("java", "https://dev.java/learn/"),
            Map.entry("python", "https://docs.python.org/3/tutorial/"),
            Map.entry("javascript", "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide"),
            Map.entry("typescript", "https://www.typescriptlang.org/docs/"),
            Map.entry("html", "https://developer.mozilla.org/en-US/docs/Learn/HTML"),
            Map.entry("css", "https://developer.mozilla.org/en-US/docs/Learn/CSS"),
            Map.entry("react", "https://react.dev/learn"),
            Map.entry("node.js", "https://nodejs.org/en/learn"),
            Map.entry("spring boot", "https://spring.io/guides"),
            Map.entry("sql", "https://www.postgresql.org/docs/current/tutorial-sql.html"),
            Map.entry("postgresql", "https://www.postgresql.org/docs/current/tutorial.html"),
            Map.entry("mysql", "https://dev.mysql.com/doc/refman/8.0/en/tutorial.html"),
            Map.entry("mongodb", "https://learn.mongodb.com/"),
            Map.entry("git", "https://git-scm.com/book/en/v2"),
            Map.entry("docker", "https://docs.docker.com/get-started/"),
            Map.entry("kubernetes", "https://kubernetes.io/docs/tutorials/"),
            Map.entry("aws", "https://aws.amazon.com/training/digital/"),
            Map.entry("linux", "https://ubuntu.com/tutorials/command-line-for-beginners"),
            Map.entry("pandas", "https://pandas.pydata.org/docs/getting_started/intro_tutorials/"),
            Map.entry("numpy", "https://numpy.org/learn/"),
            Map.entry("scikit-learn", "https://scikit-learn.org/stable/tutorial/index.html"),
            Map.entry("pytorch", "https://pytorch.org/tutorials/"),
            Map.entry("tensorflow", "https://www.tensorflow.org/tutorials"),
            Map.entry("machine learning", "https://developers.google.com/machine-learning/crash-course"),
            Map.entry("kotlin", "https://kotlinlang.org/docs/getting-started.html"),
            Map.entry("flutter", "https://docs.flutter.dev/get-started"),
            Map.entry("dart", "https://dart.dev/tutorials"),
            Map.entry("c#", "https://learn.microsoft.com/en-us/dotnet/csharp/"),
            Map.entry(".net", "https://learn.microsoft.com/en-us/dotnet/"),
            Map.entry("go", "https://go.dev/tour/"),
            Map.entry("rest api", "https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview"),
            Map.entry("power bi", "https://learn.microsoft.com/en-us/training/powerplatform/power-bi"),
            Map.entry("terraform", "https://developer.hashicorp.com/terraform/tutorials"),
            Map.entry("apache spark", "https://spark.apache.org/docs/latest/quick-start.html"),
            Map.entry("selenium", "https://www.selenium.dev/documentation/"),
            Map.entry("figma", "https://help.figma.com/hc/en-us/categories/360002051613"));

    private LearningResources() {
    }

    public static List<Resource> forSkill(String skill) {
        String key = skill == null ? "" : skill.trim().toLowerCase(Locale.ROOT);
        String q = URLEncoder.encode(skill == null ? "" : skill.trim(), StandardCharsets.UTF_8);
        List<Resource> list = new ArrayList<>();
        String official = OFFICIAL.get(key);
        if (official != null) {
            list.add(new Resource("Official documentation", official, "DOCS"));
        }
        list.add(new Resource("freeCodeCamp", "https://www.freecodecamp.org/news/search/?query=" + q, "ARTICLES"));
        list.add(new Resource("YouTube tutorials", "https://www.youtube.com/results?search_query=" + q + "+tutorial+for+beginners", "VIDEO"));
        list.add(new Resource("NPTEL / SWAYAM courses", "https://onlinecourses.nptel.ac.in/explorer/search?searchText=" + q, "COURSE"));
        list.add(new Resource("GeeksforGeeks", "https://www.geeksforgeeks.org/search/?gq=" + q, "PRACTICE"));
        list.add(new Resource("Coursera (free courses)", "https://www.coursera.org/search?query=" + q + "&isFree=true", "COURSE"));
        return list;
    }
}
