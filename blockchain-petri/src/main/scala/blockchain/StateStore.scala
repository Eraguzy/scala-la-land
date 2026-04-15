package blockchain

import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, NoSuchFileException, Path, Paths, StandardCopyOption, StandardOpenOption}
import scala.util.Using

// Persistance fichier avec verrou exclusif pour serialiser les commandes CLI.
object StateStore {
  val defaultRuntimeDir: Path = Paths.get("runtime")
  private val stateFileName = "blockchain-state.txt"
  private val lockFileName = ".blockchain-state.lock"

  def statePath(runtimeDir: Path = defaultRuntimeDir): Path =
    runtimeDir.resolve(stateFileName)

  private def lockPath(runtimeDir: Path): Path =
    runtimeDir.resolve(lockFileName)

  def initialize(snapshot: BlockchainSnapshot, runtimeDir: Path = defaultRuntimeDir, overwrite: Boolean = true): Unit = {
    withExclusiveLock(runtimeDir) {
      val target = statePath(runtimeDir)
      if (!overwrite && Files.exists(target)) {
        throw new IllegalStateException(s"Le fichier d'état existe déjà : $target")
      }
      saveSnapshot(snapshot, runtimeDir)
    }
  }

  def loadSnapshot(runtimeDir: Path = defaultRuntimeDir): Option[BlockchainSnapshot] = {
    val file = statePath(runtimeDir)
    try {
      val content = Files.readString(file, StandardCharsets.UTF_8)
      Some(SnapshotCodec.read(content))
    } catch {
      case _: NoSuchFileException => None
    }
  }

  def withLockedRuntime[T](runtimeDir: Path = defaultRuntimeDir)(f: ActorRuntime => T): Option[T] = {
    withExclusiveLock(runtimeDir) {
      loadSnapshot(runtimeDir).map { snapshot =>
        val runtime = ActorRuntime.fromSnapshot(snapshot)
        try {
          // Flux standard: charger -> executer une action -> sauvegarder l'etat mis a jour.
          val result = f(runtime)
          saveSnapshot(runtime.snapshot(), runtimeDir)
          result
        } finally {
          runtime.shutdown()
        }
      }
    }
  }

  private def saveSnapshot(snapshot: BlockchainSnapshot, runtimeDir: Path): Unit = {
    Files.createDirectories(runtimeDir)

    val target = statePath(runtimeDir)
    val temp = runtimeDir.resolve(s".$stateFileName.tmp")
    val content = SnapshotCodec.write(snapshot)

    Files.writeString(
      temp,
      content,
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )

    try {
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch {
      case _: AtomicMoveNotSupportedException =>
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def withExclusiveLock[T](runtimeDir: Path)(thunk: => T): T = {
    Files.createDirectories(runtimeDir)

    Using.resource(
      FileChannel.open(
        lockPath(runtimeDir),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE
      )
    ) { channel =>
      Using.resource(channel.lock()) { _ =>
        thunk
      }
    }
  }
}
