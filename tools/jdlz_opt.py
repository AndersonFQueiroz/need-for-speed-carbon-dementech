import sys
def jdlz_compress_opt(data):
    n=len(data)
    HASH=0x2000; mask=HASH-1
    head=[-1]*HASH; chain=[-1]*n
    opts=[]
    for i in range(n):
        b3 = data[i:i+3]
        if len(b3)==3 and i<n-2:
            h=(-0x1A1*(data[i] ^ (((data[i+1] ^ (data[i+2]<<4))<<4))))&mask
        else:
            h=None
        best16=0; bestlong=0; d16=0; dlong=0
        if h is not None:
            p=head[h]; cnt=0
            while p>=0 and i-p<=2064 and cnt<4096:
                d=i-p
                if d<=16:
                    lim=min(n-i,4098); m=0
                    while m<lim and data[p+m]==data[i+m]: m+=1
                    if m>=3 and m>best16: best16=m; d16=d
                else:
                    lim=min(n-i,34); m=0
                    while m<lim and data[p+m]==data[i+m]: m+=1
                    if m>=3 and m>bestlong: bestlong=m; dlong=d
                p=chain[p]; cnt+=1
        o=[]
        if best16>=3: o.append((d16,best16))
        if bestlong>=3: o.append((dlong,bestlong))
        opts.append(o)
        if h is not None:
            # insere i na cadeia DEPOIS de andar
            chain[i]=head[h]; head[h]=i
    cost=[0]*(n+1)
    for i in range(n-1,-1,-1):
        c=9+cost[i+1]
        for (d,m) in opts[i]:
            m=min(m,n-i)
            if m<3: continue
            seg=cost[i+3:i+m+1]
            mc=18+min(seg) if seg else 18+cost[i+m]
            if mc<c: c=mc
        cost[i]=c
    # reconstrucao gananciosa do caminho minimo
    toks=[]; i=0
    if n>=3: toks.append(('l',data[i])); i+=1
    else:
        for b in data: toks.append(('l',b)); return toks
    while i<n:
        c=9+cost[i+1]; best=('l',data[i]); bn=1
        for (d,m) in opts[i]:
            m=min(m,n-i)
            if m<3: continue
            mc=18+min(cost[i+3:i+m+1]) if m>3 else 18+cost[i+3]
            # busca primeiro L com cost[i+L]==min
            mv=min(cost[i+3:i+m+1]); 
            for L in range(3,m+1):
                if cost[i+L]==mv:
                    if 18+mv<c: c=18+mv; best=('r',d,L); bn=L
                    break
        if best[0]=='r':
            toks.append(best); i+=bn
        else:
            toks.append(best); i+=1
    return toks

def emit(toks, toks_data):
    n=len(toks_data)
    header = bytearray(b'ZLDJ\x02\x10\x00\x00')
    header += n.to_bytes(4,'big')
    header += b'\x00\x00\x00\x00'
    body = bytearray()
    flags1bit=1; flags2bit=1; flags1=0; flags2=0
    slots={}
    f1pos=len(body); body.append(0); slots['f1']=f1pos
    f2pos=len(body); body.append(0); slots['f2']=f2pos
    flags1bit <<= 1
    ti=0
    # o 1 token do DP forca literal; emit aqui
    body.append(toks[0][1]); ti=1
    for tk in toks[1:]:
        if tk[0]=='l':
            body.append(tk[1])
        else:
            _,d,ln=tk
            flags1 |= flags1bit
            if d<17:
                flags2 |= flags2bit
                b0=(d-1)|(((ln-3)>>4)&0xF0); b1=(ln-3)&0xFF
                body.append(b0); body.append(b1)
            else:
                dd=d-17
                b0=((ln-3)&0x1F)|((dd>>3)&0xE0); b1=dd&0xFF
                body.append(b0); body.append(b1)
            flags2bit=(flags2bit<<1)&0xFF
        flags1bit=(flags1bit<<1)&0xFF
        if flags1bit==0:
            body[slots['f1']]=flags1; flags1=0
            slots['f1']=len(body); body.append(0)
            flags1bit=1
        if flags2bit==0:
            body[slots['f2']]=flags2; flags2=0
            slots['f2']=len(body); body.append(0)
            flags2bit=1
    if flags2bit>1:
        body[slots['f2']]=flags2
    elif slots['f2']==len(body)-1:
        del body[slots['f2']]; slots['f2']=None
    if flags1bit>1:
        body[slots['f1']]=flags1
    elif slots['f1']==len(body)-1:
        del body[slots['f1']]; slots['f1']=None
    total=len(body)+16
    header[12:16]=total.to_bytes(4,'big')
    return bytes(header+body)
