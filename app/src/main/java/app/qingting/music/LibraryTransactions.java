package app.qingting.music;
import java.util.concurrent.Executor;
import org.json.JSONObject;
/** Worker owns the committed document; each published state is a separate object. */
public final class LibraryTransactions {
 public interface Edit {void apply(LibraryState value)throws Exception;}
 public interface Sink {boolean commit(String json)throws Exception;}
 public interface Completion {void complete(LibraryState state,String error);}
 private String committed;private final Executor worker,publisher;private final Sink sink;
 public LibraryTransactions(String initial,Executor worker,Executor publisher,Sink sink){committed=initial;this.worker=worker;this.publisher=publisher;this.sink=sink;}
 public void submit(Edit edit,Completion callback){worker.execute(()->{LibraryState next=null;String error=null;try{LibraryState staged=LibraryState.from(new JSONObject(committed));edit.apply(staged);String json=staged.json().toString();if(!sink.commit(json))throw new java.io.IOException("本地保存失败，请检查存储空间");committed=json;next=staged;}catch(Exception e){error=e.getMessage()==null?"本地保存失败":e.getMessage();}LibraryState result=next;String problem=error;publisher.execute(()->callback.complete(result,problem));});}
}
