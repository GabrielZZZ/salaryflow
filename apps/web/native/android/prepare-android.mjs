import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { cp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const webRoot = resolve(here, '../..')
const androidRoot = join(webRoot, 'android')
const appRoot = join(androidRoot, 'app')
const gradlePath = join(appRoot, 'build.gradle')
const versionName = process.env.ANDROID_VERSION_NAME || '0.1.0-dev'
const versionCodeRaw = Number.parseInt(process.env.ANDROID_VERSION_CODE || '1', 10)
const versionCode = Number.isFinite(versionCodeRaw) && versionCodeRaw > 0 ? versionCodeRaw : 1
const releaseSigning = process.env.ANDROID_RELEASE_SIGNING === 'true'

// 模板包含小组件全套原生代码（java / manifest / res）。
// CI 每次用 `cap add android` 重新生成工程，必须用模板覆盖回来，
// 否则发布包会丢失桌面小组件（MainActivity 的插件注册也在模板里）。
const templateRoot = join(here, 'template')
await cp(templateRoot, androidRoot, { recursive: true })

// 清理 macOS 文件同步产生的 "xxx 2.xml" 类幽灵副本（会导致资源合并失败）
async function purgeGhostDuplicates(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) await purgeGhostDuplicates(path)
    else if (/ \d+\.\w+$/.test(entry.name)) await rm(path)
  }
}
await purgeGhostDuplicates(join(androidRoot, 'app/src/main'))

// 启动器图标直接用圆角方形 PNG：adaptive icon 会被启动器形状遮罩裁掉边角。
// CI 的 `cap add android` 会恢复 Capacitor 默认 adaptive xml，故每次覆盖后删除。
for (const rel of [
  'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
  'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',
  'app/src/main/res/drawable-v24/ic_launcher_foreground.xml',
  ...['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'].map(
    (d) => `app/src/main/res/mipmap-${d}/ic_launcher_foreground.png`,
  ),
]) {
  await rm(join(androidRoot, rel), { force: true })
  await rm(join(templateRoot, rel), { force: true })
}

await mkdir(join(appRoot, 'src/main/java/com/salaryflow/app/widget'), { recursive: true })

let gradle = await readFile(gradlePath, 'utf8')
// 安装身份与 capacitor.config.json 的 appId 保持一致。换过包名：OEM 启动器（ColorOS 等）
// 按包名缓存图标与小组件视图树，新身份让这些缓存整体失效。namespace 仍为 com.salaryflow.app。
gradle = gradle
  .replace(/applicationId\s+"[^"]+"/, 'applicationId "com.salaryflow.android"')
  // cap add 会用 appId 生成 namespace；Java 源码与 R 都在 com.salaryflow.app，必须钉回去
  .replace(/namespace\s*=?\s*"[^"]+"/, 'namespace = "com.salaryflow.app"')
  .replace(/versionCode\s+\d+/, `versionCode ${versionCode}`)
  .replace(/versionName\s+"[^"]+"/, `versionName "${versionName}"`)

// ponytail: 签名钥匙放 ~/.android（android/ 目录会被 rm -rf 清掉，之前钥匙跟着
// 一起丢过）。密码是本地开发签名用的固定值，不做真分发信任根。
if (releaseSigning) {
  const jks = join(homedir(), '.android', 'salaryflow-release.jks')
  const propsPath = join(androidRoot, 'keystore.properties')
  if (!existsSync(propsPath)) {
    if (!existsSync(jks)) {
      const keytool = process.env.JAVA_HOME
        ? join(process.env.JAVA_HOME, 'bin', 'keytool')
        : 'keytool'
      execFileSync(
        keytool,
        [
          '-genkeypair', '-keystore', jks, '-keyalg', 'RSA', '-keysize', '2048',
          '-validity', '10000', '-alias', 'salaryflow',
          '-storepass', 'salaryflow', '-keypass', 'salaryflow',
          '-dname', 'CN=SalaryFlow',
        ],
        { stdio: 'pipe' },
      )
    }
    await writeFile(
      propsPath,
      `storeFile=${jks}\nstorePassword=salaryflow\nkeyAlias=salaryflow\nkeyPassword=salaryflow\n`,
    )
  }
}

if (releaseSigning) {
  if (!gradle.includes('salaryflowReleaseSigning')) {
    gradle = gradle.replace(/android\s*\{/, `android {
    // salaryflowReleaseSigning
    signingConfigs {
        release {
            def keystorePropertiesFile = rootProject.file("keystore.properties")
            def keystoreProperties = new Properties()
            keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
        }
    }`)
  }
  gradle = gradle.replace(/(buildTypes\s*\{\s*release\s*\{)/, '$1\n            signingConfig signingConfigs.release')
}

await writeFile(gradlePath, gradle)
console.log(`Prepared Android project: versionName=${versionName}, versionCode=${versionCode}, releaseSigning=${releaseSigning}`)
