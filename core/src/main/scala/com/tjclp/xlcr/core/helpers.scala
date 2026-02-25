package com.tjclp.xlcr.core

import org.apache.poi.hsmf.MAPIMessage

import zio.blocks.scope.{ Resource, Unscoped }

import com.tjclp.xlcr.types.DynamicFragment

// Unscoped instances so Chunk/Fragment types can escape Scope.global.scoped blocks
given unscopedChunk[A: Unscoped]: Unscoped[zio.Chunk[A]] with {}
given Unscoped[DynamicFragment] with                          {}

// Resource factories for POI types — used with scope.allocate for compile-time leak prevention
private[core] def mapiMessageResource(
  msg: => MAPIMessage
): Resource[MAPIMessage] =
  Resource.acquireRelease(msg)(_.close())
