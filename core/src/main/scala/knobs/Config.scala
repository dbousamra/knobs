//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package knobs

/** Immutable configuration data */
case class Config(env: Env) {
  def subconfig(g: Name): Config = {
    val pfx = g + (if (g.isEmpty) "" else ".")
    Config(env.collect {
      case (key, value) if key.startsWith(pfx) =>
        (key.substring(pfx.length), value)
    })
  }

  def ++(cfg: Config): Config =
    Config(env ++ cfg.env)

  /** Look up the value under the key with the given name */
  def lookup[A:Configured](name: Name): Option[A] =
    env.get(name).flatMap(_.convertTo[A])

  /** Look up the value under the key with the given name and error if it doesn't exist */
  def require[A:Configured](name: Name): A =
    lookup(name).getOrElse(env.get(name).fold(throw KeyError(name))(v => throw ValueError(name, v)))
}

object Config {
  val empty = Config(Map())

  def parse(s: String): Either[Throwable, Config] = {
    import ConfigParser._
    def go(pfx: String, acc: Env, ds: List[Directive]): Env =
      ds.foldLeft(acc)((m, d) => d match {
        case Bind(name, v) => m + ((pfx + name) -> v)
        case Group(name, gds) => go(pfx + name + ".", m, gds)
        case x => sys.error(s"Unexpected directive: $x")
      })
    runParser(sansImport, s) match {
      case Left(e)        => Left(ParseException(e.message.toString))
      case Right((_, ds)) => Right(Config(go("", empty.env, ds)))
    }
  }
}
