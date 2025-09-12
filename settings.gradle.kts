// settings.gradle.kts (v1)

// يبدأ بالبحث عن المجلدات من الدليل الرئيسي للمشروع
rootDir.listFiles()?.forEach { file ->
    // يتأكد أن ما وجده هو مجلد وليس ملفًا، ويستثني المجلدات الخاصة مثل .git و .github
    if (file.isDirectory && !file.name.startsWith(".")) {
        // يتحقق من وجود ملف الإعدادات الخاص بالإضافة داخل المجلد
        val buildFile = file.resolve("build.gradle.kts")
        if (buildFile.exists()) {
            // إذا وجده، يقوم بتضمين هذا المجلد كـ "وحدة" أو "إضافة" في المشروع
            include(file.name)
        }
    }
}
