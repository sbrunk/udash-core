package io.udash.properties
package seq

import java.util.UUID

import io.udash.properties.single._
import io.udash.utils.{Registration, SetRegistration}

import scala.collection.mutable

private[properties]
abstract class BaseReadableSeqPropertyFromSingleValue[A, B : PropertyCreator](origin: ReadableProperty[A], transformer: A => Seq[B])
  extends ReadableSeqProperty[B, ReadableProperty[B]] {

  override val id: UUID = PropertyCreator.newID()
  override protected[properties] def parent: ReadableProperty[_] = null

  protected final val structureListeners: mutable.Set[Patch[Property[B]] => Any] = mutable.Set()
  protected final val children = CrossCollections.createArray[Property[B]]

  update(origin.get)
  origin.listen(update)

  private def structureChanged(patch: Patch[Property[B]]): Unit =
    CallbackSequencer().queue(
      s"${this.id.toString}:fireElementsListeners:${patch.hashCode()}",
      () => structureListeners.foreach(_.apply(patch))
    )

  private def update(v: A): Unit = {
    val transformed = transformer(v)
    val current = get

    def commonIdx(s1: Iterator[B], s2: Iterator[B]): Int =
      math.max(0,
        s1.zipAll(s2, null, null).zipWithIndex
          .indexWhere { case (((x, y), _)) => x != y })

    val commonBegin = commonIdx(transformed.iterator, current.iterator)
    val commonEnd = commonIdx(transformed.reverseIterator, current.reverseIterator)

    val patch = if (transformed.size > current.size) {
      val added: Seq[CastableProperty[B]] = Seq.fill(transformed.size - current.size)(implicitly[PropertyCreator[B]].newProperty(this))
      CrossCollections.replace(children, commonBegin, 0, added: _*)
      Some(Patch(commonBegin, Seq(), added, clearsProperty = false))
    } else if (transformed.size < current.size) {
      val removed = CrossCollections.slice(children, commonBegin, commonBegin + current.size - transformed.size)
      CrossCollections.replace(children, commonBegin, current.size - transformed.size)
      Some(Patch(commonBegin, removed, Seq(), transformed.isEmpty))
    } else None

    CallbackSequencer().sequence {
      transformed.zip(children)
        .slice(commonBegin, math.max(commonBegin + transformed.size - current.size, transformed.size - commonEnd))
        .foreach { case (pv, p) => p.set(pv) }
      patch.foreach(structureChanged)
      valueChanged()
    }
  }

  override def elemProperties: Seq[ReadableProperty[B]] =
    children.map(_.transform((id: B) => id))

  override def get: Seq[B] =
    children.map(_.get)
}

private[properties]
class ReadableSeqPropertyFromSingleValue[A, B : PropertyCreator](origin: ReadableProperty[A], transformer: A => Seq[B])
  extends BaseReadableSeqPropertyFromSingleValue(origin, transformer) {
  /** Registers listener, which will be called on every property structure change. */
  override def listenStructure(structureListener: (Patch[ReadableProperty[B]]) => Any): Registration = {
    structureListeners += structureListener
    new SetRegistration(structureListeners, structureListener)
  }
}

private[properties]
class SeqPropertyFromSingleValue[A, B : PropertyCreator](origin: Property[A], transformer: A => Seq[B], revert: Seq[B] => A)
  extends BaseReadableSeqPropertyFromSingleValue[A, B](origin, transformer) with SeqProperty[B, Property[B]] {

  override protected[properties] def valueChanged(): Unit = {
    super.valueChanged()
    origin.set(revert(get))
  }

  override def replace(idx: Int, amount: Int, values: B*): Unit = {
    val current = mutable.ListBuffer(get: _*)
    current.remove(idx, amount)
    current.insertAll(idx, values)
    origin.set(revert(current))
  }

  override def set(t: Seq[B], force: Boolean = false): Unit =
    origin.set(revert(t), force)

  override def setInitValue(t: Seq[B]): Unit =
    origin.setInitValue(revert(t))

  override def touch(): Unit =
    origin.touch()

  override def elemProperties: Seq[Property[B]] =
    children

  override def listenStructure(structureListener: (Patch[Property[B]]) => Any): Registration = {
    structureListeners += structureListener
    new SetRegistration(structureListeners, structureListener)
  }

  /** Removes all listeners from property. */
  override def clearListeners(): Unit = {
    super.clearListeners()
    structureListeners.clear()
  }
}
