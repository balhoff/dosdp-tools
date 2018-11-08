package org.monarchinitiative.dosdp

sealed trait Binding

final case class SingleValue(value: String) extends Binding

final case class MultiValue(value: Set[String]) extends Binding
