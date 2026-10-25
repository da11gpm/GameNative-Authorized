# GameNative Authorized: manifiestos locales

Implementación nueva sobre GameNative master `dc24544101ef53543614041d7a8d8af68738090a`
(24 de octubre de 2026). Ese árbol declara 1.4.0 / versionCode 24; la última
versión oficial publicada comprobada el 25 de octubre es 1.3.0, commit
`0be4545b34a814dc7227125b3f112ced90706d4b`.

Este fork se identifica como `app.gamenative.authorized`, versión
`1.4.0-authorized.1`, nombre visible GameNative Authorized. Se instala junto
a la aplicación oficial y necesita su propia sesión de Steam.

## Uso

1. Inicia sesión y sincroniza tu biblioteca de Steam.
2. Abre Ajustes → LuaTools · manifiestos locales.
3. Introduce el AppID de un juego de tu biblioteca y confirma que tienes
   autorización para usar el manifiesto y su contenido.
4. Selecciona un archivo UTF-8 `.lua` local, de hasta 64 KiB.
5. Instala o actualiza el juego desde su página, en la rama `public`.

La importación sustituye las referencias anteriores de ese AppID. Se guardan
por cuenta y juego y permanecen activas hasta pulsar Eliminar referencias.
Para cambiar una instalación existente usa Actualizar; Instalar conserva el
comportamiento de upstream y puede omitir depósitos ya instalados. Verificar
repara la versión registrada como instalada. Las otras ramas no usan estas referencias.

Ejemplo sintético para entender el formato, **no descargable**:

```lua
addappid(42)
setManifestid(43, "123")
```

Se admiten comentarios de línea, BOM UTF-8, puntos y coma y tamaños opcionales
en `setManifestid`. Los GID conservan los 64 bits sin signo. Se rechazan
depósitos duplicados, valores inválidos, comentarios de bloque, tokens y
cualquier sentencia ajena a este subconjunto. El AppID inicial debe coincidir
y cada depósito debe existir en los metadatos Steam del juego.

Si una línea `addappid(depot, 1, "<clave hexadecimal de 64 caracteres>")`
contiene una clave, se descarta. El archivo original, claves y tokens no se
guardan ni se registran. Solo persisten AppID, depot ID y manifest GID
saneados, en preferencias privadas sin copia de seguridad de Android.

## Autorización y límites

La aplicación comprueba la licencia de biblioteca no caducada al importar y
al iniciar una descarga con referencias. Esta comprobación local es un filtro;
la autorización efectiva sigue en Steam: cada depósito necesita una clave de
32 bytes otorgada por Steam y cada manifiesto importado un código de solicitud
explícito. Un error al obtener ese código no habilita una alternativa local.
Se conserva la selección de DLC de upstream. No se insertan juegos ni licencias.

No hay catálogo, fuentes remotas de manifiestos, extracción ZIP, carga de Lua,
inyección de claves, suplantación de propiedad ni instalación de parches.
La integración admite referencias de versiones autorizadas; no incorpora el
plugin completo de LuaTools. Steam puede rechazar versiones antiguas aunque
la cuenta tenga el juego. Los tamaños mostrados antes de descargar pueden
seguir reflejando los metadatos actuales, no los de la versión seleccionada.

## Comparación con Unchained

Se revisó `Kaal31/Unchained` en
`55a06e289228aaca6351ffee55b9ff3fe213b857` (19 de julio de 2026), que declara
GameNative 1.1.0 / versionCode 19. Su release publicada es `v.0.0.1 alpha`.

| Área | Unchained | Esta implementación |
| --- | --- | --- |
| Entrada | Proveedores remotos y paquetes | Archivo local elegido por el usuario |
| Datos Lua | Claves, tokens, GID y archivos binarios | Únicamente referencias AppID/depot/GID |
| Biblioteca | Inserta juegos y licencias sintéticas | Exige juego en la biblioteca existente |
| Descarga | Extiende DepotDownloader de JavaSteam e inserta claves | Usa el motor Rust actual y la autorización de Steam |
| Caché | Precoloca manifiestos y SHA-1 en el formato antiguo | No introduce archivos binarios en la caché |
| Parches | Instalador comunitario y overrides DLL | No incluido |

Unchained no comparte el historial como fork GitHub: comienza con una copia
inicial. El diff de SteamService respecto a la etiqueta oficial 1.1.0 incluye
también cambios ajenos a LuaTools. No sería correcto aplicar todo ese diff al
árbol actual. El motor actual usa `GameDownloadService`/`NativeSteamDownload`
y la caché separada `target/` y `completed/`, por lo que el hook antiguo no
es directamente compatible.

## Licencias

GameNative incluye LICENSE GPL versión 3 y sus avisos declaran la aplicación
GPL-3.0. Se conservan esos archivos y atribuciones. Los archivos nuevos de
esta integración se distribuyen bajo GPL-3.0-or-later.

El README de Unchained declara GPL-3.0, pero su commit `590fabb` eliminó
LICENSE. El archivo estaba en su commit inicial y coincide con el de
GameNative 1.1.0. GitHub devuelve license=null en el estado revisado. Esta
incoherencia documental no se presenta aquí como una relicencia; no se copió
su implementación.

El APK completo hereda componentes con licencias distintas y binarios de
upstream con código retenido o licencia propietaria. Consulta
THIRD_PARTY_NOTICES; el APK no se describe como íntegramente GPL ni como
recompilable por completo desde fuentes públicas. Compilar este fork reutiliza
los binarios nativos distribuidos por upstream. No se reconstruyeron esos
binarios ni se añadieron juegos, claves o parches de terceros.

## Compilación y pruebas

Se necesitan JDK 17 y Android SDK 36. La configuración actual utiliza los
binarios nativos ya presentes; no activa CMake/NDK en la variante modern.

```sh
./gradlew :app:bundleModernDebug
./gradlew :app:testModernDebugUnitTest --tests 'app.gamenative.luatools.*'
```

El bundle está en `app/build/outputs/bundle/modernDebug/`. Para generar un
APK único con el módulo dinámico fusionado, utiliza el bundletool incluido:

```sh
java -jar tools/bundletool-all-1.17.2.jar build-apks \
  --bundle=app/build/outputs/bundle/modernDebug/app-modern-debug.aab \
  --output=authorized.apks --mode=universal
unzip -p authorized.apks universal.apk > gamenative-authorized.apk
```

Bundletool utiliza una clave debug local por defecto. Para usar otra firma,
consulta sus opciones `--ks` y `--ks-key-alias`. Una recompilación no tendrá
necesariamente el mismo hash: la firma local y las dependencias SNAPSHOT
pueden cambiar. Los hashes entregados identifican los archivos de esta ejecución.

Modern requiere
Android 10 o posterior y ARM64. La firma debug es para pruebas.
El entorno requiere un archivo local.properties, aunque esté vacío; para
especificar el SDK añade `sdk.dir=/ruta/al/sdk`. No incluyas credenciales.

Los tests comprueban el parser, el saneamiento, el aislamiento por cuenta,
la revocación por licencia caducada, los depósitos desconocidos, la eliminación
de referencias y el rechazo de importaciones sin conexión. Las pruebas de
almacenamiento usan Android simulado con Robolectric y servicios Steam simulados. No equivalen a una prueba real de descarga, sesión,
selección de archivos o arranque de juegos. Se necesita un dispositivo ARM64,
una cuenta y contenido autorizado para validar esos pasos.
