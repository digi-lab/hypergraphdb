package org.hypergraphdb.peer.workflow;

import static org.hypergraphdb.peer.HGDBOntology.*;
import static org.hypergraphdb.peer.Messages.createMessage;
import static org.hypergraphdb.peer.Structs.combine;
import static org.hypergraphdb.peer.Structs.getPart;
import static org.hypergraphdb.peer.Structs.struct;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HyperGraph;
import org.hypergraphdb.peer.PeerFilter;
import org.hypergraphdb.peer.PeerFilterEvaluator;
import org.hypergraphdb.peer.PeerInterface;
import org.hypergraphdb.peer.PeerRelatedActivity;
import org.hypergraphdb.peer.PeerRelatedActivityFactory;
import org.hypergraphdb.peer.Subgraph;
import org.hypergraphdb.peer.SubgraphManager;
import org.hypergraphdb.peer.protocol.Performative;
import org.hypergraphdb.peer.workflow.RememberTaskClient.State;
import org.hypergraphdb.query.HGAtomPredicate;
import org.hypergraphdb.query.HGQueryCondition;

public class QueryTaskClient extends TaskActivity<QueryTaskClient.State>
{
	protected enum State {Started, Done}

	private AtomicInteger count = new AtomicInteger(1);

	private PeerFilterEvaluator evaluator = null;
	private Iterator<Object> targets = null;
	
	private HGHandle handle;
	private HGQueryCondition cond;
	private boolean getObject;
	private HyperGraph tempGraph;
	
	private ArrayList<Object> result;
	
	public QueryTaskClient(PeerInterface peerInterface, HyperGraph tempGraph)
	{
		super(peerInterface, State.Started, State.Done);
		
		this.tempGraph = tempGraph;
	
	}
	
	public QueryTaskClient(PeerInterface peerInterface, HyperGraph tempGraph, PeerFilterEvaluator evaluator, HGQueryCondition cond, boolean getObject)
	{
		super(peerInterface, State.Started, State.Done);
	
		this.evaluator = evaluator;
		this.handle = null;
		this.cond = cond;
		this.getObject = getObject;
		this.tempGraph = tempGraph;
	}
	public QueryTaskClient(PeerInterface peerInterface, HyperGraph tempGraph, Iterator<Object> targets, HGQueryCondition cond, boolean getObject)
	{
		super(peerInterface, State.Started, State.Done);
	
		this.targets  = targets;
		this.handle = null;
		this.cond = cond;
		this.getObject = getObject;
		this.tempGraph = tempGraph;
	}



	public QueryTaskClient(PeerInterface peerInterface, HyperGraph tempGraph, PeerFilterEvaluator evaluator, HGHandle handle)
	{
		super(peerInterface, State.Started, State.Done);
		
		this.evaluator = evaluator;
		this.handle = handle;
		this.getObject = true;
		this.tempGraph = tempGraph;
	}
	public QueryTaskClient(PeerInterface peerInterface, HyperGraph tempGraph, Iterator<Object> targets, HGHandle handle)
	{
		super(peerInterface, State.Started, State.Done);
		
		this.targets = targets;
		this.handle = handle;
		this.getObject = true;
		this.tempGraph = tempGraph;
	}
	
	private Iterator<Object> getTargets()
	{
		if (targets != null)
		{
			return targets;
		}else{
			PeerFilter peerFilter = getPeerInterface().newFilterActivity(evaluator);
			peerFilter.filterTargets();
			return peerFilter.iterator();			
		}
	}
	
	@Override
	protected void startTask()
	{		
		PeerRelatedActivityFactory activityFactory = getPeerInterface().newSendActivityFactory();
		Iterator<Object> it = getTargets();
		
		//do startup tasks - filter peers and send messages
		while (it.hasNext())
		{
			Object target = it.next();
			sendMessage(activityFactory, target);
		}			
		
		if (count.decrementAndGet() == 0)
		{
			setState(State.Done);
		}

	}

	private void sendMessage(PeerRelatedActivityFactory activityFactory, Object target)
	{
		count.incrementAndGet();

		Object msg = createMessage(Performative.Request, QUERY, getTaskId());
		combine(msg, struct(CONTENT, 
				struct(SLOT_QUERY, (handle == null) ? cond : handle,
						SLOT_GET_OBJECT, getObject)));
				
		PeerRelatedActivity activity = (PeerRelatedActivity)activityFactory.createActivity();
		activity.setTarget(target);
		activity.setMessage(msg);
		
		getPeerInterface().execute(activity);
	}
	
	
	public void handleMessage(Object msg)
	{
		//get result
		ArrayList<?> reply = (ArrayList<?>)getPart(msg, CONTENT);
		result = new ArrayList<Object>();

		for(int i=0;i<reply.size();i++)
		{
			Object elem = getPart(reply, i);
			if (elem instanceof Subgraph)
			{
				result.add(SubgraphManager.get((Subgraph) elem, tempGraph));				
			}else{
				result.add(elem);
			}
			
		}
		
		if (count.decrementAndGet() == 0)
		{
			setState(State.Done);
		}
	}

	public ArrayList<Object> getResult()
	{
		return result;
	}

	public void setResult(ArrayList<Object> result)
	{
		this.result = result;
	}
	
	
}