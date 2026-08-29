import { cp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises'
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

await mkdir(join(appRoot, 'src/main/java/com/salaryflow/app/widget'), { recursive: true })

let gradle = await readFile(gradlePath, 'utf8')
gradle = gradle
  .replace(/versionCode\s+\d+/, `versionCode ${versionCode}`)
  .replace(/versionName\s+"[^"]+"/, `versionName "${versionName}"`)

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
