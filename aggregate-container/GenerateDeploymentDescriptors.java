import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resourceSpecifier.factory.*;
import org.apache.uima.resourceSpecifier.factory.impl.ServiceContextImpl;
import org.apache.uima.util.InvalidXMLException;
import org.xml.sax.SAXException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by tmill on 10/16/18.
 */
public class GenerateDeploymentDescriptors {
    public static void main(String[] args) throws ResourceInitializationException, IOException, InvalidXMLException, SAXException {
    	String brokerUrl = String.format("https://%s:%d", System.getProperty("broker_host"), Integer.parseInt(System.getProperty("broker_port")));
		// generate the delegate analysis engine (remoteFull.xml)
//		AnalysisEngineDescription mistAed = AnalysisEngineFactory.createEngineDescriptionFromPath("desc/docker-mist.xml", "brokerURL", brokerUrl);
//		AnalysisEngineDescription ctakesAed = AnalysisEngineFactory.createEngineDescriptionFromPath("desc/docker-fast-dictionary.xml", "brokerURL", brokerUrl);
//		AnalysisEngineDescription writerAed = AnalysisEngineFactory.createEngineDescriptionFromPath("desc/docker-writer.xml", "brokerURL", brokerUrl);
//
//		AggregateBuilder agg = new AggregateBuilder();
//		agg.add(mistAed);
//		agg.add(ctakesAed);
//		agg.add(writerAed);
//		agg.createAggregateDescription().toXML(new FileWriter("remoteFull-auto.xml"));

    	// generate the deployment descriptor (aggregateDeploymentDescriptor.xml)
        ServiceContext context =
			new ServiceContextImpl("Aggregate AWS AE",
				           "Aggregate AE connecting all remote container AEs",
				           "remoteFull.xml",
				           "mainQueue", brokerUrl);

        DelegateConfiguration mistConfig = DeploymentDescriptorFactory.createRemoteDelegateConfiguration("remoteMist",
				brokerUrl,
				"mistQueue",
				SerializationStrategy.binary);

        DelegateConfiguration dictConfig = DeploymentDescriptorFactory.createRemoteDelegateConfiguration("dockerFastDictionary",
				brokerUrl,
				"myQueueName",
				SerializationStrategy.binary);

        DelegateConfiguration writerConfig = DeploymentDescriptorFactory.createRemoteDelegateConfiguration("remoteI2b2Writer",
				brokerUrl,
				"writerQueue",
				SerializationStrategy.binary);

		UimaASAggregateDeploymentDescriptor dd =
				DeploymentDescriptorFactory.
						createAggregateDeploymentDescriptor(context,
								mistConfig, dictConfig, writerConfig);
		dd.setAsync(true);
		dd.setCasPoolSize(10);

		String ddXML = dd.toXML();

		BufferedWriter out = new BufferedWriter(new FileWriter("aggregateDeploymentDescriptor-auto.xml"));
		out.write(ddXML);
		out.close();
	}
}
