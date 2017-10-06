package org.monarchinitiative.dosdp.cli

import scala.collection.JavaConverters._

import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.rdf.model.ModelFactory
import org.backuity.clist._
import org.monarchinitiative.dosdp._
import org.phenoscape.owlet.Owlet
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.elk.owlapi.ElkReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.OWLOntology

import com.github.tototoshi.csv.CSVWriter

import uk.ac.manchester.cs.jfact.JFactFactory

object Query extends Command(description = "query an ontology for terms matching a Dead Simple OWL Design Pattern") with Common {

  var reasonerNameOpt = opt[Option[String]](name = "reasoner", description = "Reasoner to use for expanding variable constraints. Valid options are ELK, HermiT, or JFact.")
  var printQuery = opt[Boolean](name = "print-query", default = false, description = "Print generated query without running against ontology")

  def run: Unit = {
    val sepFormat = tabularFormat
    val sparqlQuery = SPARQL.queryFor(ExpandedDOSDP(inputDOSDP, prefixes))
    val reasonerFactoryOpt = reasonerNameOpt.map(_.toLowerCase).map { name =>
      name match {
        case "elk"    => new ElkReasonerFactory()
        case "hermit" => new ReasonerFactory()
        case "jfact"  => new JFactFactory()
        case other    => throw new RuntimeException(s"Reasoner $other not supported. Options are ELK, HermiT, or JFact")
      }
    }
    val processedQuery = (ontologyOpt, reasonerFactoryOpt) match {
      case (None, Some(_)) => throw new RuntimeException("Reasoner requested but no ontology specified; exiting.")
      case (Some(ontology), Some(factory)) =>
        val reasoner = factory.createReasoner(ontology)
        val owlet = new Owlet(reasoner)
        owlet.expandQueryString(sparqlQuery)
      case (_, None) => sparqlQuery
    }
    if (printQuery) {
      println(processedQuery)
    } else {
      if (ontOpt.isEmpty) throw new RuntimeException("Can't run query; no ontology provided.")
      val model = ModelFactory.createDefaultModel()
      val allAxioms = for {
        mainOnt <- ontologyOpt.toSet[OWLOntology]
        ont <- mainOnt.getImportsClosure.asScala
        axiom <- ont.getAxioms().asScala
      } yield axiom
      val manager = OWLManager.createOWLOntologyManager()
      val triples = SesameJena.ontologyAsTriples(manager.createOntology(allAxioms.asJava))
      model.add(triples.toList.asJava)
      val query = QueryFactory.create(processedQuery)
      val results = QueryExecutionFactory.create(query, model).execSelect()
      val columns = results.getResultVars.asScala.toList
      val writer = CSVWriter.open(outfile, "utf-8")(sepFormat)
      writer.writeRow(columns)
      while (results.hasNext()) {
        val qs = results.next()
        writer.writeRow(columns.map(variable => Option(qs.get(variable)).map(_.toString).getOrElse("")))
      }
      writer.close()
    }
  }

}