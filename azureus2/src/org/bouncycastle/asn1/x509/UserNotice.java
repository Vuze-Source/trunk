
package org.bouncycastle.asn1.x509;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DERSequence;

/**
 * <code>UserNotice</code> class, used in
 * <code>CertificatePolicies</code> X509 extensions (in policy
 * qualifiers).
 * <pre>
 * UserNotice ::= SEQUENCE {
 *      noticeRef        NoticeReference OPTIONAL,
 *      explicitText     DisplayText OPTIONAL}
 *
 * </pre>
 * 
 * @see PolicyQualifierId
 * @see PolicyInformation
 */
public class UserNotice extends ASN1Encodable 
{

   NoticeReference noticeRef;
   DisplayText explicitText;
   
   public DERObject toASN1Object() 
      {
         ASN1EncodableVector av = new ASN1EncodableVector();
         av.add(noticeRef);
         av.add(explicitText);
         return new DERSequence(av);
      }

   /**
    * Creates a new <code>UserNotice</code> instance.
    *
    * @param noticeRef a <code>NoticeReference</code> value
    * @param explicitText a <code>DisplayText</code> value
    */
   public UserNotice (NoticeReference noticeRef, DisplayText explicitText) 
      {
         this.noticeRef = noticeRef;
         this.explicitText = explicitText;
      }

   /**
    * Creates a new <code>UserNotice</code> instance.
    *
    * @param noticeRef a <code>NoticeReference</code> value
    * @param str the explicitText field as a String. 
    */
   public UserNotice (NoticeReference noticeRef, String str) 
      {
         this.noticeRef = noticeRef;
         this.explicitText = new DisplayText (str);
      }

   /**
    * Creates a new <code>UserNotice</code> instance.
    * <p>Useful from reconstructing a <code>UserNotice</code> instance
    * from its encodable/encoded form. 
    *
    * @param as an <code>ASN1Sequence</code> value obtained from either
    * calling @{link toASN1Object()} for a <code>UserNotice</code>
    * instance or from parsing it from a DER-encoded stream. 
    */
   public UserNotice (ASN1Sequence as) 
      {
         NoticeReference nr =
            new NoticeReference (
               ASN1Sequence.getInstance(as.getObjectAt(0)));

         if (as.size() > 1) {
            explicitText =
               DisplayText.getInstance(ASN1Sequence.getInstance(
                                   as.getObjectAt(1)));
         }
      }
}
