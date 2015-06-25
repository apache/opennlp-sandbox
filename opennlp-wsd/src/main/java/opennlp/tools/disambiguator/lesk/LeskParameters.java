package opennlp.tools.disambiguator.lesk;

public class LeskParameters {

  // VARIATIONS
  public static enum LESK_TYPE {
    LESK_BASIC, LESK_BASIC_CTXT, LESK_BASIC_CTXT_WIN, LESK_BASIC_CTXT_WIN_BF, LESK_EXT, LESK_EXT_CTXT, LESK_EXT_CTXT_WIN, LESK_EXT_CTXT_WIN_BF, LESK_EXT_EXP, LESK_EXT_EXP_CTXT, LESK_EXT_EXP_CTXT_WIN, LESK_EXT_EXP_CTXT_WIN_BF,
  }

  // DEFAULTS
  protected static final LESK_TYPE DFLT_LESK_TYPE = LESK_TYPE.LESK_EXT_EXP_CTXT_WIN;
  protected static final int DFLT_WIN_SIZE = 4;
  protected static final int DFLT_DEPTH = 3;
  protected static final double DFLT_IEXP = 0.3;
  protected static final double DFLT_DEXP = 0.3;

  public LESK_TYPE leskType;
  public int win_f_size;
  public int win_b_size;
  public int depth;

  public boolean fathom_synonyms;
  public boolean fathom_hypernyms;
  public boolean fathom_hyponyms;
  public boolean fathom_meronyms;
  public boolean fathom_holonyms;

  public double depth_weight;
  public double iexp;
  public double dexp;

  public LeskParameters() {
    this.setDefaults();
  }

  public void setDefaults() {
    this.leskType = LeskParameters.DFLT_LESK_TYPE;
    this.win_f_size = LeskParameters.DFLT_WIN_SIZE;
    this.win_b_size = LeskParameters.DFLT_WIN_SIZE;
    this.depth = LeskParameters.DFLT_DEPTH;
    this.iexp = LeskParameters.DFLT_IEXP;
    this.dexp = LeskParameters.DFLT_DEXP;
    this.fathom_holonyms = true;
    this.fathom_hypernyms = true;
    this.fathom_hyponyms = true;
    this.fathom_meronyms = true;
    this.fathom_synonyms = true;
  }

  // Parameter Validation
  // TODO make isSet for semantic feature booleans
  public boolean isValid() {

    switch (this.leskType) {
    case LESK_BASIC:
    case LESK_BASIC_CTXT:
      return true;
    case LESK_BASIC_CTXT_WIN:
      return (this.win_b_size == this.win_f_size) && this.win_b_size >= 0;
    case LESK_BASIC_CTXT_WIN_BF:
      return (this.win_b_size >= 0) && (this.win_f_size >= 0);
    case LESK_EXT:
    case LESK_EXT_CTXT:
      return (this.depth >= 0) && (this.depth_weight >= 0);

    case LESK_EXT_CTXT_WIN:
    case LESK_EXT_CTXT_WIN_BF:
      return (this.depth >= 0) && (this.depth_weight >= 0)
          && (this.win_b_size >= 0) && (this.win_f_size >= 0);

    case LESK_EXT_EXP:
    case LESK_EXT_EXP_CTXT:
      return (this.depth >= 0) && (this.dexp >= 0) && (this.iexp >= 0);

    case LESK_EXT_EXP_CTXT_WIN:
    case LESK_EXT_EXP_CTXT_WIN_BF:
      return (this.depth >= 0) && (this.dexp >= 0) && (this.iexp >= 0)
          && (this.win_b_size >= 0) && (this.win_f_size >= 0);
    default:
      return false;
    }
  }

}

