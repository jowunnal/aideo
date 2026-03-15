# M2M100 JNI 브릿지 코드 분석

> Android NDK 환경에서 Kotlin/Java와 C++를 연결하는 JNI(Java Native Interface) 코드를 분석한다.
> 대상 파일: `features/core/src/main/cpp/m2m100_jni.cpp`

---

## 파일 구조 개요

이 파일은 Kotlin 클래스 `M2M100Native`의 네이티브 메서드를 C++로 구현한 **JNI 브릿지**다.
Java/Kotlin 세계와 C++ 세계 사이에서 데이터를 변환하고 전달하는 역할만 수행하며, 실제 번역 로직은 `M2M100Translator` 클래스에 위임한다.

```
Kotlin (M2M100Native) ──JNI 브릿지──▶ C++ (M2M100Translator)
       jstring, jobjectArray 등           std::string, std::vector 등
```

### 제공하는 JNI 함수 (6개)

| 함수 | 역할 |
|------|------|
| `initialize` | 번역기 인스턴스 생성 |
| `loadModel` | ONNX 모델 및 토크나이저 로드 |
| `translateWithBuffer` | DirectByteBuffer 기반 단건 번역 |
| `translateBatch` | 문자열 배열 배치 번역 |
| `isLanguageSupported` | 언어 지원 여부 확인 |
| `release` | 리소스 해제 |

---

## 1. 헤더와 전역 상태

```cpp
#include <jni.h>       // JNI 타입과 함수 (시스템 헤더)
#include <string>      // std::string (C++ 표준 라이브러리)
#include "m2m100_translator.h"  // 프로젝트 내부 헤더

static M2M100Translator* g_translator = nullptr;

extern "C" {
```

### 학습 포인트

**`#include`의 두 가지 형태**
- `< >`: 시스템/라이브러리 헤더. 컴파일러가 시스템 경로에서 검색한다.
- `" "`: 프로젝트 내부 헤더. 현재 디렉토리부터 검색한다.

`#include`는 **전처리기 지시문**으로, 컴파일 전에 해당 파일의 내용을 그대로 복사해 넣는다. `#define`(매크로 치환)도 같은 전처리 단계에서 동작한다.

**`static` 전역 변수**
파일 스코프의 `static`은 이 변수의 가시성을 **현재 파일 내부로 제한**한다. 다른 `.cpp` 파일에서 `g_translator`에 접근할 수 없다.

**`nullptr`**
C++11에서 도입된 타입 안전한 널 포인터. C의 `NULL`(정수 0)과 달리 포인터 타입으로만 사용 가능하다.

**`extern "C"`**
C++ 컴파일러는 함수 오버로딩을 지원하기 위해 **네임 맹글링(name mangling)** 을 수행한다 — 함수명에 파라미터 타입 정보를 인코딩해서 변경한다. `extern "C"`는 이 맹글링을 비활성화하여, JVM이 약속된 함수명(예: `Java_jinproject_aideo_...`)으로 함수를 찾을 수 있게 한다.

> **참고 — Java는 네임 맹글링이 없다.**
> Java는 컴파일 시 바이트코드에 **메서드 디스크립터**(파라미터와 반환 타입 정보)를 별도로 기록해서 오버로딩을 구분한다. 함수명 자체를 변경할 필요가 없다.

---

## 2. initialize — 번역기 인스턴스 생성

```cpp
JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_initialize(
        JNIEnv* env,
        jobject /* this */) {
    if (g_translator != nullptr) {
        return JNI_TRUE;
    }
    g_translator = new M2M100Translator();
    return JNI_TRUE;
}
```

### 학습 포인트

**JNI 함수 시그니처의 구성 요소**

| 요소 | 설명 |
|------|------|
| `JNIEXPORT` | 매크로. 함수를 공유 라이브러리 외부에 노출 (visibility 설정) |
| `jboolean` | 반환 타입. Java의 `boolean`에 대응 |
| `JNICALL` | 매크로. 호출 규약(calling convention) 지정 |
| `Java_{패키지}_{클래스}_{메서드}` | JVM이 네이티브 함수를 찾는 명명 규칙 |

`JNIEXPORT`, `JNICALL`, `JNI_TRUE`/`JNI_FALSE`는 모두 **매크로**로, 전처리 단계에서 플랫폼별 값으로 치환된다.

**JNI 필수 파라미터**
- 1번째: `JNIEnv* env` — JNI 함수 테이블에 대한 포인터. JVM과 소통하는 모든 JNI 함수는 이 포인터를 통해 호출한다.
- 2번째: `jobject` (인스턴스 메서드) 또는 `jclass` (정적 메서드) — Java의 `this`에 해당. 사용하지 않을 경우 `/* this */` 주석으로 표시한다.

**`new` 키워드와 힙 할당**
`new M2M100Translator()`는 **힙(heap) 메모리**에 객체를 생성한다. 스택 변수와 달리 함수가 끝나도 자동으로 해제되지 않으며, 반드시 `delete`로 명시적 해제가 필요하다. 해제하지 않으면 **메모리 누수**가 발생한다.

> **`new`와 `delete`의 대응**은 이 파일의 `release()` 함수에서 확인할 수 있다.

**가드 절(Guard Clause)**
`g_translator != nullptr` 검사로 중복 생성을 방지한다. 이미 초기화되었으면 즉시 반환하는 **얼리 리턴** 패턴이다.

---

## 3. loadModel — 모델 로드

```cpp
JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_loadModel(
        JNIEnv* env,
        jobject /* this */,
        jstring encoderPath,
        jstring decoderPath,
        jstring decoderWithPastPath,
        jstring spModelPath,
        jstring vocabPath,
        jstring tokenizerConfigPath) {

    if (g_translator == nullptr) {
        return JNI_FALSE;
    }

    const char* encoder = env->GetStringUTFChars(encoderPath, nullptr);
    // ... (6개 문자열 모두 동일 패턴)

    bool result = g_translator->load(encoder, decoder, decoderWithPast,
                                     spModel, vocab, tokenizerConfig);

    env->ReleaseStringUTFChars(encoderPath, encoder);
    // ... (6개 모두 Release)

    return result ? JNI_TRUE : JNI_FALSE;
}
```

### 학습 포인트

**`jstring`은 직접 사용할 수 없다**
`jstring`은 JVM 내부 문자열 객체에 대한 **불투명 포인터(opaque pointer)** 다. 실제 문자 데이터에 접근하려면 JNI 함수를 통해 변환해야 한다.

**Get/Release 패턴**

```cpp
const char* str = env->GetStringUTFChars(javaString, nullptr);
// str 사용
env->ReleaseStringUTFChars(javaString, str);
```

`GetStringUTFChars`는 Java 문자열(UTF-16)을 **Modified UTF-8로 변환한 복사본**을 네이티브 메모리에 생성한다. 이 메모리는 **JNI 런타임이 관리**하므로, 반드시 `ReleaseStringUTFChars`로 해제해야 한다 (`free`나 `delete` 사용 불가).

> **`isCopy` 파라미터 (두 번째 인자)**
> `GetStringUTFChars(javaString, &isCopy)`처럼 `jboolean*`을 넘기면, JVM이 복사본을 만들었는지(`JNI_TRUE`) 직접 참조를 줬는지(`JNI_FALSE`)를 알려준다. `nullptr`을 넘기면 "알 필요 없음"의 의미다.
>
> 복사본이 아닌 직접 참조를 받은 경우, `Release`는 JVM이 해당 메모리를 GC에서 이동하지 못하도록 고정(pin)한 것을 **해제(unpin)** 하는 역할을 한다.
>
> 단, Java 내부는 UTF-16이고 C++에는 UTF-8로 변환해야 하므로, 실제로는 거의 항상 복사본이 생성된다.

**`const char*`의 의미**
`const`가 `*` 앞에 있으므로 **포인터가 가리키는 내용이 상수**라는 뜻이다. 포인터 자체는 다른 주소를 가리킬 수 있다.

```
const char* p;    // 가리키는 내용이 const (내용 수정 불가, 포인터 변경 가능)
char* const p;    // 포인터 자체가 const (포인터 변경 불가, 내용 수정 가능)
const char* const p; // 둘 다 const
```

**암시적 변환 (Implicit Conversion)**
`g_translator->load()`의 실제 시그니처는 `const std::string&` 파라미터를 받는다. `const char*`를 전달하면 `std::string`의 **변환 생성자**가 자동으로 호출되어 임시 `std::string` 객체가 생성된다. 이 때 문자열 데이터가 한 번 더 복사된다.

```
jstring → GetStringUTFChars → const char* (1차 복사)
       → std::string 암시적 변환 (2차 복사)
```

> **왜 `const std::string&`가 아닌 `const char*`로 받는가?**
> `translate()` 등의 C++ 함수는 JNI에 의존하지 않는 범용 API로 설계되었다. C++ 관례상 문자열 파라미터는 `const std::string&`로 받는 것이 표준이다. JNI 브릿지에서 `const char*`를 넘기면 암시적 변환이 일어나지만, 이는 API 설계의 독립성과 안전성을 위한 트레이드오프다.

**`->` 화살표 연산자**
포인터를 통해 멤버에 접근하는 연산자. `g_translator->load()`는 `(*g_translator).load()`와 동일하다.

**삼항 연산자**
`result ? JNI_TRUE : JNI_FALSE` — 조건이 참이면 `JNI_TRUE`, 거짓이면 `JNI_FALSE`를 반환한다.

---

## 4. translateWithBuffer — DirectByteBuffer 기반 번역

```cpp
JNIEXPORT jstring JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_translateWithBuffer(
        JNIEnv* env,
        jobject /* this */,
        jobject textBuffer,     // ByteBuffer
        jint textLength,
        jstring srcLang,
        jstring tgtLang,
        jint maxLength) {

    if (g_translator == nullptr) {
        return nullptr;
    }

    const char* textStr = static_cast<const char*>(
        env->GetDirectBufferAddress(textBuffer));
    if (textStr == nullptr) {
        return nullptr;
    }

    std::string text(textStr, textLength);

    const char* srcLangStr = env->GetStringUTFChars(srcLang, nullptr);
    const char* tgtLangStr = env->GetStringUTFChars(tgtLang, nullptr);

    std::string result = g_translator->translate(
        text, srcLangStr, tgtLangStr, maxLength);

    env->ReleaseStringUTFChars(srcLang, srcLangStr);
    env->ReleaseStringUTFChars(tgtLang, tgtLangStr);

    if (result.empty()) {
        return nullptr;
    }

    return env->NewStringUTF(result.c_str());
}
```

### 학습 포인트

**DirectByteBuffer와 제로카피 접근**
Java에서 `ByteBuffer.allocateDirect()`로 생성한 버퍼는 **JVM 힙 외부의 네이티브 메모리**에 할당된다. `GetDirectBufferAddress()`는 이 메모리의 포인터를 직접 반환하므로 **복사가 발생하지 않는다**.

`GetStringUTFChars` 방식과 비교:

| | `GetStringUTFChars` | `GetDirectBufferAddress` |
|---|---|---|
| 복사 | UTF-16 → UTF-8 변환 복사 | 없음 (포인터 직접 반환) |
| 적합한 상황 | 짧은 문자열, 단발성 호출 | 큰 데이터, 반복 호출 (버퍼 재사용) |
| 오버헤드 | 호출마다 복사 비용 | 버퍼 생성/관리 비용 (1회) |

> **"반복 호출"의 의미**: 같은 앱 세션에서 JNI 함수가 여러 번 호출되는 상황. 예를 들어 실시간 자막 번역 시 자막 한 줄마다 `translate()`가 호출된다. DirectByteBuffer는 **버퍼를 한 번 만들어두고 내용만 덮어쓰며 재사용**할 수 있어, 반복 호출 시 할당/복사 비용을 줄인다.

**`static_cast<const char*>`**
`GetDirectBufferAddress`의 반환 타입은 `void*`(타입 없는 범용 포인터)다. 이를 `const char*`로 변환하기 위해 **명시적 타입 캐스팅**을 수행한다. C++의 `static_cast`는 컴파일 타임에 타입 호환성을 검증한다.

**길이 지정 `std::string` 생성자**
`std::string text(textStr, textLength)` — 포인터와 길이를 지정해서 문자열을 생성한다. DirectByteBuffer의 데이터는 널 종료(`\0`)가 보장되지 않으므로, 길이를 명시적으로 전달해야 안전하다.

**`NewStringUTF` — C++ → Java 문자열 변환**
`GetStringUTFChars`의 반대 방향. C++의 UTF-8 문자열(`const char*`)로부터 Java `String` 객체를 생성한다.

**데이터 흐름 요약**

```
Java ByteBuffer ──GetDirectBufferAddress──▶ const char* (복사 없음)
     → std::string(ptr, len) 생성
         → translate() 실행 → std::string 결과
             → NewStringUTF() → Java String 반환
```

---

## 5. translateBatch — 배치 번역

```cpp
JNIEXPORT jobjectArray JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_translateBatch(
        JNIEnv* env,
        jobject /* this */,
        jobjectArray texts,
        jstring srcLang,
        jstring tgtLang,
        jint maxLength) {
    // ... (guard clause 생략)

    // 입력: Java String[] → C++ vector<string>
    jsize textCount = env->GetArrayLength(texts);
    std::vector<std::string> inputTexts;
    inputTexts.reserve(textCount);

    for (jsize i = 0; i < textCount; ++i) {
        jstring text = static_cast<jstring>(
            env->GetObjectArrayElement(texts, i));
        const char* textStr = env->GetStringUTFChars(text, nullptr);
        inputTexts.emplace_back(textStr);
        env->ReleaseStringUTFChars(text, textStr);
        env->DeleteLocalRef(text);
    }

    // 번역 실행
    const char* srcLangStr = env->GetStringUTFChars(srcLang, nullptr);
    const char* tgtLangStr = env->GetStringUTFChars(tgtLang, nullptr);
    std::vector<std::string> results = g_translator->translateBatch(
            inputTexts, srcLangStr, tgtLangStr, maxLength);
    env->ReleaseStringUTFChars(srcLang, srcLangStr);
    env->ReleaseStringUTFChars(tgtLang, tgtLangStr);

    // 출력: C++ vector<string> → Java String[]
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(
            static_cast<jsize>(results.size()), stringClass, nullptr);

    for (size_t i = 0; i < results.size(); ++i) {
        jstring resultStr = env->NewStringUTF(results[i].c_str());
        env->SetObjectArrayElement(
            resultArray, static_cast<jsize>(i), resultStr);
        env->DeleteLocalRef(resultStr);
    }

    env->DeleteLocalRef(stringClass);
    return resultArray;
}
```

### 학습 포인트

**`std::vector<std::string>` — C++의 동적 배열**
Java의 `ArrayList<String>`과 유사하다. `<std::string>`은 템플릿 파라미터로, 이 벡터가 `std::string`을 담는다는 의미다.

**`reserve(textCount)` — 메모리 사전 확보**
벡터는 원소를 추가할 때 내부 배열이 가득 차면 **더 큰 배열을 새로 할당하고 기존 데이터를 전부 복사**한다. 원소 개수를 미리 알고 있다면 `reserve`로 공간을 확보해서 이 재할당을 방지할 수 있다.

**`emplace_back` vs `push_back`**
`emplace_back(textStr)`는 `const char*`로부터 `std::string`을 **벡터 내부 메모리에서 직접 생성**한다. `push_back`은 임시 객체를 먼저 만들고 벡터에 복사/이동하므로, `emplace_back`이 불필요한 복사를 줄일 수 있다.

**`DeleteLocalRef` — JNI 로컬 참조 정리**
`GetObjectArrayElement`가 반환하는 `jobject`는 **로컬 참조(local reference)** 로 관리된다. 로컬 참조는 JNI 함수가 끝나면 자동으로 해제되지만, 로컬 참조 테이블의 크기에 한도가 있다 (기본 512개). **루프 안에서 참조가 계속 쌓이면** 테이블이 넘칠 수 있으므로, 사용이 끝난 참조는 `DeleteLocalRef`로 즉시 해제한다.

`NewStringUTF`로 생성한 `jstring`과 `FindClass`로 얻은 `jclass`도 동일하게 로컬 참조이므로 정리가 필요하다.

**입출력 대칭 구조**

```
[입력] Java String[] → 루프 { jstring → const char* → std::string } → vector
           ↓
    C++ translateBatch() 실행
           ↓
[출력] vector → 루프 { string → NewStringUTF → jstring } → Java String[]
```

입력과 출력 모두 **루프 안에서 타입 변환 + 로컬 참조 정리**를 수행하는 대칭적 패턴이다.

---

## 6. isLanguageSupported / release

나머지 두 함수는 이전에 다룬 패턴의 반복이다.

```cpp
// isLanguageSupported: Get/Release + 삼항 연산자 패턴
const char* langStr = env->GetStringUTFChars(lang, nullptr);
bool result = g_translator->isLanguageSupported(langStr);
env->ReleaseStringUTFChars(lang, langStr);
return result ? JNI_TRUE : JNI_FALSE;

// release: new에 대응하는 delete + nullptr 초기화
g_translator->release();   // 내부 리소스 해제
delete g_translator;       // 객체 메모리 해제
g_translator = nullptr;    // 댕글링 포인터 방지
```

`release()`에서 `delete` 후 `nullptr`을 대입하는 이유: 삭제된 메모리를 가리키는 포인터를 **댕글링 포인터(dangling pointer)** 라고 한다. 이후 `g_translator != nullptr` 검사가 정상 동작하도록 `nullptr`로 초기화한다.

---

## 핵심 패턴 정리

### JNI 문자열 처리의 두 가지 방식

```
방식 1: GetStringUTFChars (일반적)
  jstring → GetStringUTFChars → const char* → 사용 → ReleaseStringUTFChars

방식 2: DirectByteBuffer (최적화)
  ByteBuffer → GetDirectBufferAddress → const char* → 사용 (Release 불필요)
```

### JNI 브릿지 코드의 공통 구조

```
1. Guard clause (g_translator == nullptr 검사)
2. Java 타입 → C++ 타입 변환 (Get 계열)
3. C++ 함수 호출
4. 리소스 해제 (Release 계열)
5. C++ 타입 → Java 타입 변환 (New 계열)
6. 반환
```

### 메모리 소유권 규칙

| 할당 방식 | 소유자 | 해제 방법 |
|-----------|--------|-----------|
| `GetStringUTFChars` | JNI 런타임 | `ReleaseStringUTFChars` |
| `GetDirectBufferAddress` | Java (ByteBuffer) | 해제 불필요 (Java GC가 관리) |
| `new` | 개발자 | `delete` |
| `std::string`, `std::vector` | C++ (RAII) | 자동 해제 (스코프 종료 시) |