buildscript {
    configurations.classpath {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
            force("com.google.dagger:dagger:2.53.1")
            force("com.google.dagger:hilt-compiler:2.53.1")
            force("com.google.dagger:hilt-android-gradle-plugin:2.53.1")
        }
    }
}
