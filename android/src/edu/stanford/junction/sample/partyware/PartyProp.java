package edu.stanford.junction.sample.partyware;

import java.util.*;
import org.json.JSONObject;
import org.json.JSONException;
import edu.stanford.junction.props2.*;
import edu.stanford.junction.extra.JSONObjWrapper;

public class PartyProp extends Prop {

	public PartyProp(String propName, String propReplicaName, IPropState s){
		super(propName, propReplicaName, s);
	}

	public PartyProp(String propName){
		this(propName, propName + (new Random()).nextInt(), new PartyState());
	}

	public IProp newFresh(){
		return new PartyProp(getPropName(), getPropReplicaName(), new PartyState());
	}

	public void forceChangeEvent(){
		dispatchChangeNotification(EVT_ANY, null);
	}

	protected IPropState reifyState(JSONObject obj){
		return new PartyState(obj);
	}

	protected JSONObject newAddObjOp(JSONObject item){
		JSONObject obj = new JSONObject();
		try{
			obj.put("type", "addObj");
			obj.put("item", item);
		}catch(JSONException e){}
		return obj;
	}

	protected JSONObject newVoteOp(String itemId, int count){
		JSONObject obj = new JSONObject();
		try{
			obj.put("type", "vote");
			obj.put("itemId", itemId);
			obj.put("count", count);
		}catch(JSONException e){}
		return obj;
	}

	protected void addObj(JSONObject item){
		addOperation(newAddObjOp(item));
	}


	/////////////////////////
    // Conveniance helpers //
    /////////////////////////

	public String getName(){
		return ((PartyState)getState()).getName();
	}

	public List<JSONObject> getImages(){
		PartyState s = (PartyState)getState();
		return s.getImages();
	}

	public List<JSONObject> getUsers(){
		PartyState s = (PartyState)getState();
		return s.getUsers();
	}

	public List<JSONObject> getYoutubeVids(){
		PartyState s = (PartyState)getState();
		return s.getYoutubeVids();
	}

	public void updateUser(String userId, String name, String email, String imageUrl){
		addObj(newUserObj(userId, name, email, imageUrl));
	}

	public void addImage(String userId, String url, String thumbUrl, String caption, long time){
		addObj(newImageObj(userId, url, thumbUrl, caption, time));
	}

	public void upvoteVideo(String id){
		addOperation(newVoteOp(id, 1));
	}

	public void downvoteVideo(String id){
		addOperation(newVoteOp(id, -1));
	}

	public void addYoutube(String userId, String videoId, String thumbUrl, String caption, long time){
		addObj(newYoutubeObj(userId, videoId, thumbUrl, caption, time));
	}

	protected JSONObject newImageObj(String userId, String url, String thumbUrl, String caption, long time){
		JSONObject obj = newHTTPResourceObj("image", userId, url, caption, time);
		try{
			obj.put("thumbUrl", thumbUrl);
		}catch(JSONException e){}
		return obj;
	}

	protected JSONObject newYoutubeObj(String userId, String videoId, 
									   String thumbUrl, String caption, long time){
		JSONObject obj = newHTTPResourceObj("youtube", userId, null, caption, time);
		try{
			obj.put("videoId", videoId);
			obj.put("thumbUrl", thumbUrl);
			obj.put("votes", 0);
		}catch(JSONException e){}
		return obj;
	}

	protected JSONObject newHTTPResourceObj(String type, String userId, 
											String url, String caption, long time){
		JSONObject obj = new JSONObject();
		try{
			obj.put("id", (UUID.randomUUID()).toString());
			obj.put("type", type);
			obj.put("url", url);
			obj.put("time", time);
			obj.put("caption", caption);
			obj.put("owner", userId);
		}catch(JSONException e){}
		return obj;
	}

	protected JSONObject newUserObj(String userId, String name, String email, String imageUrl) {
		JSONObject obj = new JSONObject();
		try{
			obj.put("id", userId);
			obj.put("type", "user");
			obj.put("name", name);
			obj.put("email", email);
			obj.put("imageUrl", imageUrl);
		}catch(JSONException e){}
		return obj;
	}


	//////////////////////////
    // The State definition //
    //////////////////////////

	static class PartyState implements IPropState {

		private HashMap<String, JSONObject> objects = new HashMap<String, JSONObject>();
		private String name = "Unnamed Party";
		private int hashCode = 0;

		public PartyState(PartyState other){
			if(other != null){
				this.name = other.getName();
				Iterator it = other.objects.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String,JSONObject> pair = 
						(Map.Entry<String,JSONObject>)it.next();
					if(pair.getValue() instanceof JSONObjWrapper){
						JSONObjWrapper obj = (JSONObjWrapper)pair.getValue();
						objects.put(pair.getKey(), (JSONObject)obj.clone());
					}
				}
			}
		}

		public PartyState(JSONObject obj){
			this.name = obj.optString("name");
			JSONObject jsonObjects = obj.optJSONObject("objects");
			if(jsonObjects != null){
				Iterator it = jsonObjects.keys();
				while(it.hasNext()){
					Object key = it.next();
					Object val = jsonObjects.opt(key.toString());
					if(val instanceof JSONObject){
						JSONObject eaObj = (JSONObject)val;
						String id = eaObj.optString("id");
						objects.put(id, new JSONObjWrapper(eaObj));
					}
				}
			}
		}

		public PartyState(){
			this((PartyState)null);
		}

		public String getName(){
			return this.name;
		}
		
		public IPropState applyOperation(JSONObject op){
			String type = op.optString("type");
			if(type.equals("addObj")){
				JSONObject item = op.optJSONObject("item");
				String id = item.optString("id");
				objects.put(id, (new JSONObjWrapper(item)));
			}
			else if(type.equals("deleteObj")){
				String id = op.optString("itemId");
				objects.remove(id);
			}
			else if(type.equals("setName")){
				String name = op.optString("name");
				this.name = name;
			}
			else if(type.equals("vote")){
				String id = op.optString("itemId");
				int count = op.optInt("count");
				JSONObject o = objects.get(id);
				if(o != null){
					int cur = o.optInt("votes");
					try{
						o.put("votes", cur + count);
					}
					catch(JSONException e){
						e.printStackTrace(System.err);
					}
				}
				else{
					System.err.println("Couldn't find object for id: " + id);
				}
			}
			else{
				throw new IllegalStateException("Unrecognized operation: " + type);
			}
			return this;
		}

		protected void updateHashCode(){
			this.hashCode = 0;
			Iterator<JSONObject> it = objects.values().iterator();
			while (it.hasNext()){
				JSONObject ea = it.next();
				String id = ea.optString("id");
				this.hashCode ^= id.hashCode();
			}
			this.hashCode ^= this.name.hashCode();
		}

		public int hashCode(){
			return this.hashCode;
		}

		public JSONObject toJSON(){
			JSONObject obj = new JSONObject();
			JSONObject jsonObjects = new JSONObject();
			try{
				obj.put("name", this.name);
				obj.put("objects", jsonObjects);
			}
			catch(JSONException e){
				e.printStackTrace(System.err);
				throw new IllegalStateException("toJson failed in PartyProp!");
			}
			Iterator it = objects.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String,JSONObject> pair = 
					(Map.Entry<String,JSONObject>)it.next();
				try{
					JSONObjWrapper wrapper = (JSONObjWrapper)pair.getValue();
					jsonObjects.put(String.valueOf(pair.getKey()), 
									wrapper.getRaw());
				}
				catch(JSONException e){
					e.printStackTrace(System.err);
					throw new IllegalStateException("toJson failed in PartyProp!");
				}
			}
			return obj;
		}

		public List<JSONObject> getImages(){
			List<JSONObject> images = getObjectsOfType("image");
			sortByTime(images, true);
			return Collections.unmodifiableList(images);
		}

		public List<JSONObject> getUsers(){
			List<JSONObject> users = getObjectsOfType("user");
			Collections.sort(users, new Comparator<JSONObject>(){
					public int compare(JSONObject o1, JSONObject o2) {
						return o2.optString("name").compareTo(o1.optString("name"));
					}
				});
			return Collections.unmodifiableList(users);
		}

		public List<JSONObject> getYoutubeVids(){
			List<JSONObject> vids = getObjectsOfType("youtube");
			sortByVotes(vids, true);
			return Collections.unmodifiableList(vids);
		}

		protected List<JSONObject> getObjectsOfType(String tpe){
			ArrayList<JSONObject> objs = new ArrayList<JSONObject>();
			Iterator<JSONObject> it = objects.values().iterator();
			while (it.hasNext()) {
				JSONObject ea = it.next();
				String type = ea.optString("type");
				if(type.equals(tpe)){
					objs.add(ea);
				}
			}
			return objs;
		}


		private void sortByTime(List<JSONObject> input, final boolean newToOld){
			Collections.sort(input, new Comparator<JSONObject>(){
					public int compare(JSONObject o1, JSONObject o2) {
						if(newToOld){
							return (int)(o2.optLong("time") - o1.optLong("time"));
						}
						else{
							return (int)(o1.optLong("time") - o2.optLong("time"));
						}
					}
				});
		}

		private void sortByVotes(List<JSONObject> input, final boolean highToLow){
			Collections.sort(input, new Comparator<JSONObject>(){
					public int compare(JSONObject o1, JSONObject o2) {
						if(highToLow){
							int v1 = (int)(o1.optInt("votes"));
							int v2 = (int)(o2.optInt("votes"));
							long t1 = (o1.optLong("time"));
							long t2 = (o2.optLong("time"));
							if(v1 == v2) return (int)(t1 - t2);
							else return (v2 - v1);
						}
						else{
							return (int)(o1.optInt("votes") - o2.optInt("votes"));
						}
					}
				});
		}


		public IPropState copy(){
			return new PartyState(this);
		}
	}

}