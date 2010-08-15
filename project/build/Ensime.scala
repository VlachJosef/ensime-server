import sbt._
import sbt.FileUtilities._
import java.io.File

class EnsimeProject(info: ProjectInfo) extends DefaultProject(info){

  import Configurations.{Compile, CompilerPlugin, Default, Provided, Runtime, Test}

  val ant = "org.apache.ant" % "ant" % "1.8.1" % "compile;runtime;test"
  val ivy = "org.apache.ivy" % "ivy" % "2.1.0" % "compile;runtime;test"
  val maven = "org.apache.maven" % "maven-ant-tasks" % "2.1.0" % "compile;runtime;test"
  val bcel = "org.apache.bcel" % "bcel" % "5.2" % "compile;runtime;test"
  val scalatest = "org.scalatest" % "scalatest" % "1.2" % "test"


  //  override def compileOptions = compileOptions("-g") ++ super.compileOptions.toList

  // Copy the ensime.jar, scala-library.jar and scala-compiler.jar to 
  // the bin directory, for conveniant running.
  lazy val stage = task {

    FileUtilities.clean(path("dist"), log)

    log.info("Copying runtime environment to ./dist....")

    createDirectories(List(
	path("dist"), 
	"dist" / "bin",
	"dist" / "lib",
	"dist" / "elisp"
      ), log)


    // Copy the emacs lisp to dist
    val elisp = "src" / "main" / "elisp" ** "*.el"
    copyFlat(elisp.get, "dist" / "elisp", log)


    // Copy all the runtime dependencies over to dist
    copyFile(jarPath, "dist" / "lib" / "ensime.jar", log)
    copyFlat(mainDependencies.scalaJars.get, "dist" / "lib", log)
    val deps = fullClasspath(Runtime).get.filter(f => !(f.isDirectory))
    copyFlat(deps, "dist" / "lib", log)


    // Grab all jars..
    val cpLibs = ("dist" / "lib" ** "*.jar").get.map{ p => 
      p.toString.replace("./dist/", "")
    }

    def writeScript(classpath:String, from:String, to:String){
      val tmplF = new File(from)
      readString(tmplF,log) match {
	case Right(tmpl) => {
	  val s = tmpl.replace("<RUNTIME_CLASSPATH>", classpath)
	  val f = new File(to)
	  write(f, s, log)
	  f.setExecutable(true)	
	}
	case _ => { 
	  log.error("Failed to load script template.") 
	}
      }
    }

    // Expand the server invocation script templates.

    writeScript(cpLibs.mkString(":"), 
      "./etc/scripts/server.sh",
      "./dist/bin/server.sh")

    writeScript("\"" + cpLibs.mkString(";") + "\"", 
      "./etc/scripts/server.bat",
      "./dist/bin/server.bat")

    copyFile(path("README.md"), "dist" / "README.md", log)
    copyFile(path("LICENSE"), "dist" / "LICENSE", log)


  } dependsOn(`package`) describedAs("Build the deployment directory structure.")


  lazy val dist = task {
    val archiveName = "ensime-" + version + ".tar.gz"
    log.info("Compressing to " + archiveName + "...")
    Process("tar -pcvzf " + archiveName + " ./dist")!!(log)
    None     
  } dependsOn(stage) describedAs("Compress the deployment directory.")


  lazy val publish_manual = task {
    log.info("Converting manual to html..")
    val target = "/tmp/ensime_manual.html"
    val cwd = Some(new File("etc"))
    doSh("cat manual_head.html > " + target, cwd)!!log
    doSh("tth -r -u -Lmanual < manual.ltx >> " + target, cwd)!!(log)
    doSh("cat manual_tail.html >> " + target, cwd)!!log
    log.info("Publishing manual to web...")
    doSh("scp " + target + " www@aemon.com:~/public/aemon/file_dump/", cwd)!!(log)
    None     
  } describedAs("Publish the manual.")


  private def doSh(str:String, cwd:Option[File]) = Process("sh" :: "-c" :: str :: Nil, cwd)


}

