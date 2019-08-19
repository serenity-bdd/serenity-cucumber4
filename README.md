# Getting started with Serenity and Cucumber 4

Serenity BDD is a library that makes it easier to write high quality automated acceptance tests, with powerful reporting and living documentation features. It has strong support for both web testing with Selenium, and API testing using RestAssured. 

Serenity strongly encourages good test automation design, and supports several design patterns, including classic Page Objects, the newer Lean Page Objects/ Action Classes approach, and the more sophisticated and flexible Screenplay pattern.

## The starter project
The best place to start with Serenity and Cucumber is to clone or download the starter project on Github ([https://github.com/serenity-bdd/serenity-cucumber4-starter](https://github.com/serenity-bdd/serenity-cucumber4-starter)). This project gives you a basic project setup, along with some sample tests and supporting classes. There are two versions to choose from. The master branch uses a more classic approach, using action classes and lightweight page objects, whereas the **[screenplay](https://github.com/serenity-bdd/serenity-cucumber4-starter/tree/screenplay)** branch shows the same sample test implemented using Screenplay.

### Adding the Cucumber 4 dependency
Serenity seamlessly supports both Cucumber 2.x and Cucumber 4. However, this flexibility requires a little tweaking in the build dependencies. 

If you are using Maven, you need to do the following:
- exclude the default `cucumber-core` dependency from your `serenity-core` dependency
- Replace your `serenity-cucumber` dependency with the `serenity-cucumber4` dependency
- Add dependencies on the Cucumber 4.x version of `cucumber-java` and `cucumber-junit` into your project

An example of the correctly configured dependencies is shown below:
```xml
<dependency>
    <groupId>net.serenity-bdd</groupId>
    <artifactId>serenity-core</artifactId>
    <version>2.0.70</version>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>net.serenity-bdd</groupId>
    <artifactId>serenity-cucumber4</artifactId>
    <version>1.0.21</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-java</artifactId>
    <version>4.2.0</version>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit</artifactId>
    <version>4.2.0</version>
</dependency>
```

If you are using Gradle, you need to ensure that the 4.x version of `cucumber-core` is used using the _resolutionStrategy_ element, and also add the Cucumber 4.x version of `cucumber-java` and `cucumber-junit` dependencies as mentioned above:
```Gradle
configurations.all {
    resolutionStrategy {
        force "io.cucumber:cucumber-core:4.2.0"
    }
}

dependencies {
    testCompile "net.serenity-bdd:serenity-core:2.0.70",
                "net.serenity-bdd:serenity-cucumber4:1.0.21",
                "io.cucumber:cucumber-core:4.2.0",
                "io.cucumber:cucumber-junit:4.2.0"
}
```

## Found a bug? Please read this before you raise an issue.

If you have found a defect, we are keen to hear about it! But there are a few things you can do to help us provide a fix sooner:

### Give as much context as possible.

Simply saying "The reports don't get generated" will not help us very much. Give as much context as possible, including:
  - Serenity version (serenity-core and the other serenity libraries, such as serenity-cucummber and serenity-jbehave)
  - If you are using Firefox, firefox and geckodriver version
  - If you are using Chrome, chrome and chromedriver version
  - What Operating System are you using

Also, make sure you try with the latest version of Serenity - your bug may already be fixed, and in any case error messages from the latest version will be more relevant when we try to track down the source of the problem.

### Use living documentation

It is easier for us to fix something we can see breaking. If someone has to volunteer an hour of there time to reproduce a defect, Start of with one of the Serenity started projects (like [this one](https://github.com/serenity-bdd/serenity-cucumber-starter) and add a scenario or test case that both illustrates and describes your issue. If possible, write the test to describe the behaviour you expect, so that it fails when the defect is present, and that it will pass when the defect is fixed.

### Submit a Pull Request

The fastest way to fix a defect is often to dig into the code and to submit a pull request. 

### Ask for commercial support

If you are using Serenity for your company projects, and need faster or more in-depth support, why not ask your company to get some [commercial support](https://johnfergusonsmart.com/serenity-bdd/)? We provide a range of support options including prioritied tickets, custom Serenity work, and remote mentoring/pair programming sessions, depending on your needs.

Take a look at [this article](https://opensource.guide/how-to-contribute/#communicating-effectively) for more information.


