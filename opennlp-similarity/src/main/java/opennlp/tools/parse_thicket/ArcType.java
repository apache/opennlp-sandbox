package opennlp.tools.parse_thicket;

public class ArcType{
	private String type; // rst
	private String subtype; // rst-explain
	private Integer type_id;
	private Integer subtype_id;
	
	public ArcType(String type, // rst
	String subtype, // rst-explain
	Integer type_id,
	Integer subtype_id){
		this.type = type; // rst
		this.subtype = subtype; // rst-explain
		this.type_id= type_id;
		this.subtype_id = subtype_id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSubtype() {
		return subtype;
	}

	public void setSubtype(String subtype) {
		this.subtype = subtype;
	}

	public Integer getType_id() {
		return type_id;
	}

	public void setType_id(Integer type_id) {
		this.type_id = type_id;
	}

	public Integer getSubtype_id() {
		return subtype_id;
	}

	public void setSubtype_id(Integer subtype_id) {
		this.subtype_id = subtype_id;
	}
	
	public String toString(){
		return type+":"+subtype;
	}
}