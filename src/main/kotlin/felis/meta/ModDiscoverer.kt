package felis.meta

import felis.transformer.ContentCollection
import felis.util.PerfCounter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.modules.SerializersModule
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.getIntegerOrNull
import org.slf4j.LoggerFactory
import java.nio.file.Path

typealias Modid = String

class ModDiscoverer {
    companion object {
        const val MOD_META = "felis.mod.toml"
        val metadataToml = Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
            serializersModule = SerializersModule {
                contextual(Path::class, PathSerializer)
            }
        }
    }

    private val internalMods = hashMapOf<Modid, Mod>()
    private val internalLibs = mutableListOf<ContentCollection>()
    private val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)
    private val perfcounter = PerfCounter()

    // offered to outsiders as API
    val mods: Iterable<Mod> = this.internalMods.values
    val libs: Iterable<ContentCollection> = this.internalLibs.asIterable()

    fun registerMod(mod: Mod) {
        if (mod.modid in this.internalMods) {
            throw ModDiscoveryError("Mod ${mod.modid} has already been registered")
        }

        this.internalMods[mod.modid] = mod
    }

    fun walkScanner(scanner: Scanner) {
        this.logger.info("mod discovery running for scanner $scanner")
        scanner.offer { this.perfcounter.timed { this.consider(it) } }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun consider(contentCollection: ContentCollection) {
        val result = createMods(contentCollection)

        if (result is ModDiscoveryResult.Mods) result.mods.forEach(this::registerMod)
        if (result is ModDiscoveryResult.Error) result.failedMods.forEach(Exception::printStackTrace)
        if (result is ModDiscoveryResult.NoMods) this.internalLibs.add(contentCollection)
    }

    fun finish() {
        this.perfcounter.printSummary { _, total, average ->
            this.logger.info("discovered ${this.internalMods.size} mods in ${total}s. Average discovery time was ${average}ms")
        }
    }

    private fun createMods(cc: ContentCollection): ModDiscoveryResult {
        if (cc.getContentUrl("mods.toml") != null) {
            this.logger.warn("Checked mod $cc contained a mods.toml file that is assumed to use the old metadata schema. Considering it a library.")
            this.logger.warn("To fix this, please change it's name to felis.mod.toml and use schema version 1")
            return ModDiscoveryResult.NoMods
        }
        val urls = cc.getContentUrls(MOD_META)
        if (urls.isEmpty()) return ModDiscoveryResult.NoMods

        val exceptions = mutableListOf<ModDiscoveryError>()
        val mods = mutableListOf<Mod>()
        for (url in urls) {
            val tomlString = try {
                String(url.openStream().use { it.readAllBytes() })
            } catch (e: Exception) {
                val top = ModDiscoveryError("Problem occured when reading mod candidate : $cc")
                top.initCause(e)
                exceptions += top
                continue
            }

            try {
                val metaToml = metadataToml.parseToTomlTable(tomlString)
                if (metaToml.getIntegerOrNull("schema") != 1L) {
                    exceptions += ModDiscoveryError("$url, must specify a valid schema version. Available versions are: 1")
                    continue
                }
                val metadata = metadataToml.decodeFromString<ModMetadataExtended>(tomlString)
                mods += Mod(cc, metadata)
            } catch (e: SerializationException) {
                val top = ModDiscoveryError("Mod $cc had a malformatted $MOD_META file(at $url)")
                top.initCause(e)
                exceptions += top
            }
        }

        return if (exceptions.isEmpty()) {
            ModDiscoveryResult.Success(mods)
        } else if (mods.isEmpty()) {
            ModDiscoveryResult.Failure(exceptions)
        } else {
            ModDiscoveryResult.PartialMods(mods, exceptions)
        }
    }
}
